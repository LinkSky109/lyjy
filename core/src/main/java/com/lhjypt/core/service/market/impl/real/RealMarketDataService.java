package com.lhjypt.core.service.market.impl.real;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.config.RealMarketProperties;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.service.market.MarketDataRepository;
import com.lhjypt.core.service.market.source.MarketCandle;
import com.lhjypt.core.service.market.source.MarketDataSource;
import com.lhjypt.core.service.monitor.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 真实行情接入服务（OBS-005/006 + P3-006 可靠性）：
 * 多源故障切换（gateio → binance-vision）、每源重试与退避、超时可配；
 * 拉取结果按 (symbol, 频率, bar_time) 去重写入 ClickHouse bar_data。
 */
@Service
public class RealMarketDataService {

    private static final Logger log = LoggerFactory.getLogger(RealMarketDataService.class);

    private final RealMarketProperties properties;
    private final List<MarketDataSource> sources;
    private final MarketDataRepository repository;
    private final DistributedLock distributedLock;
    private final MetricsService metricsService;

    private volatile LocalDateTime lastRefreshAt;
    private volatile int lastRefreshed;
    private volatile String lastSource;
    /** 各源运行状态：name -> {status, lastError, lastSuccessAt} */
    private final Map<String, Map<String, Object>> sourceState = new ConcurrentHashMap<>();

    /** Spring 注入构造（含业务指标）。 */
    @Autowired
    public RealMarketDataService(RealMarketProperties properties,
                                 List<MarketDataSource> sources,
                                 MarketDataRepository repository,
                                 DistributedLock distributedLock,
                                 MetricsService metricsService) {
        this.properties = properties;
        this.sources = sources == null ? List.of() : sources;
        this.repository = repository;
        this.distributedLock = distributedLock;
        this.metricsService = metricsService;
    }

    /** 便捷构造（无指标，供单元测试）。 */
    public RealMarketDataService(RealMarketProperties properties,
                                 List<MarketDataSource> sources,
                                 MarketDataRepository repository,
                                 DistributedLock distributedLock) {
        this(properties, sources, repository, distributedLock, null);
    }

    /** 定时自动刷新（间隔 >=5000 才执行；失败仅记录日志，不中断调度）。 */
    @Scheduled(fixedRateString = "${lhjypt.market.real.refresh-interval-ms:900000}")
    public void scheduledRefresh() {
        if (!properties.isEnabled() || properties.getRefreshIntervalMs() < 5000) {
            return;
        }
        distributedLock.execute("market.real.refresh", () -> {
            try {
                refresh(properties.getCurrencyPair(), properties.getInterval(),
                        properties.getLimit());
            } catch (Exception error) {
                log.warn("定时真实行情刷新失败（忽略，等待下轮）: {}", error.getMessage());
            }
        });
    }

    public int refresh(String currencyPair, String interval, Integer limit) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("真实行情源未启用（lhjypt.market.real.enabled=false）");
        }
        String pair = currencyPair == null || currencyPair.isBlank()
                ? properties.getCurrencyPair() : currencyPair;
        String freq = interval == null || interval.isBlank() ? properties.getInterval() : interval;
        int countLimit = limit == null || limit <= 0 ? properties.getLimit() : Math.min(limit, 1000);

        try {
            List<MarketCandle> candles = fetchWithFailover(pair, freq, countLimit);
            // 已存在时间戳（限最近 1000 根），跳过避免重复写入
            Set<LocalDateTime> existing = loadExisting(pair, freq);
            int inserted = persist(pair, freq, candles, existing);
            lastRefreshed = inserted;
            lastRefreshAt = LocalDateTime.now();
            if (metricsService != null) {
                metricsService.marketRefresh(inserted);
            }
            log.info("真实行情刷新完成: {}@{} 源={} 拉取 {} 新增 {}",
                    pair, freq, lastSource, candles.size(), inserted);
            return inserted;
        } catch (Exception error) {
            if (metricsService != null) {
                metricsService.marketRefreshFailure();
            }
            throw new IllegalStateException("真实行情刷新失败: " + error.getMessage(), error);
        }
    }

    /**
     * 分页回补更长历史（P4-001）：以最早一根 K 线时间为游标向前翻页，
     * 直至达到目标条数、翻完单页（不足一页）、游标无法继续前移或无新增为止。
     *
     * @param currencyPair 交易对（空取默认）
     * @param interval     K 线周期（空取默认）
     * @param total        目标条数（空 = pageSize × maxPages；上限同）
     * @param pageSize     每页条数（空取配置；上限 1000）
     * @param source       指定数据源（空则按配置顺序故障切换并粘性复用首个成功源）
     */
    public BackfillResult backfill(String currencyPair, String interval, Integer total,
                                   Integer pageSize, String source) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("真实行情源未启用（lhjypt.market.real.enabled=false）");
        }
        String pair = currencyPair == null || currencyPair.isBlank()
                ? properties.getCurrencyPair() : currencyPair;
        String freq = interval == null || interval.isBlank() ? properties.getInterval() : interval;
        int size = pageSize == null || pageSize <= 0
                ? properties.getBackfillPageSize() : Math.min(pageSize, 1000);
        size = Math.max(1, size);
        int maxPages = Math.max(1, properties.getBackfillMaxPages());
        int target = total == null || total <= 0 ? size * maxPages
                : Math.min(total, size * maxPages);
        int pageLimit = Math.max(1, Math.min(maxPages, (int) Math.ceil((double) target / size)));

        Map<String, MarketDataSource> byName = index();
        // 按时间升序累积并跨页去重
        Map<Long, MarketCandle> collected = new java.util.TreeMap<>();
        Long cursor = null;
        String preferred = null;
        String usedSource = null;
        int pages = 0;

        try {
            for (int page = 0; page < pageLimit && collected.size() < target; page++) {
                List<MarketCandle> candles = fetchPage(byName, pair, freq, size, cursor, source, preferred);
                pages++;
                if (candles == null || candles.isEmpty()) {
                    break;
                }
                int before = collected.size();
                long earliest = Long.MAX_VALUE;
                for (MarketCandle candle : candles) {
                    if (candle == null) {
                        continue;
                    }
                    collected.putIfAbsent(candle.timestamp(), candle);
                    earliest = Math.min(earliest, candle.timestamp());
                }
                usedSource = lastSource;
                preferred = usedSource;
                int added = collected.size() - before;
                MarketDataSource used = usedSource == null ? null : byName.get(usedSource);
                boolean canPaginate = used != null && used.supportsBackfill();
                // 源不支持分页 / 本页不足一页（已到历史起点）/ 本页无新增 -> 结束
                if (!canPaginate || candles.size() < size || added == 0) {
                    break;
                }
                long nextCursor = earliest - 1;
                if (cursor != null && nextCursor >= cursor) {
                    break; // 游标无法继续前移，防死循环
                }
                cursor = nextCursor;
            }

            List<MarketCandle> ordered = new ArrayList<>(collected.values());
            Set<LocalDateTime> existing = loadExisting(pair, freq, ordered);
            int inserted = persist(pair, freq, ordered, existing);
            lastRefreshed = inserted;
            lastRefreshAt = LocalDateTime.now();
            if (metricsService != null) {
                metricsService.marketRefresh(inserted);
            }
            BackfillResult result = new BackfillResult(pair, freq, pages, ordered.size(), inserted,
                    usedSource == null ? "（无）" : usedSource,
                    ordered.isEmpty() ? null : toBarTime(ordered.get(0).timestamp()),
                    ordered.isEmpty() ? null : toBarTime(ordered.get(ordered.size() - 1).timestamp()));
            log.info("行情分页回补完成: {}@{} 源={} 页={} 拉取 {} 新增 {} 区间 {} ~ {}",
                    pair, freq, result.source(), pages, result.fetched(), inserted,
                    result.earliest(), result.latest());
            return result;
        } catch (Exception error) {
            if (metricsService != null) {
                metricsService.marketRefreshFailure();
            }
            throw new IllegalStateException("行情分页回补失败: " + error.getMessage(), error);
        }
    }

    /** 分页回补结果。 */
    public record BackfillResult(String symbol, String frequency, int pages, int fetched,
                                 int inserted, String source, LocalDateTime earliest,
                                 LocalDateTime latest) {
    }

    /** 按 (symbol, frequency, bar_time) 去重写入，返回新增条数。 */
    private int persist(String pair, String freq, List<MarketCandle> candles,
                        Set<LocalDateTime> existing) {
        int inserted = 0;
        for (MarketCandle candle : candles) {
            LocalDateTime barTime = toBarTime(candle.timestamp());
            if (existing.contains(barTime)) {
                continue;
            }
            BarData bar = new BarData();
            bar.setSymbol(pair);
            bar.setFrequency(freq);
            bar.setOpen(dec(candle.open()));
            bar.setHigh(dec(candle.high()));
            bar.setLow(dec(candle.low()));
            bar.setClose(dec(candle.close()));
            bar.setVolume(toLong(candle.volume()));
            bar.setTurnover(dec(candle.amount()));
            bar.setOpenInterest(0);
            bar.setBarTime(barTime);
            repository.saveBar(bar);
            existing.add(barTime); // 防批内重复
            inserted++;
        }
        return inserted;
    }

    /**
     * 多源故障切换 + 每源重试：按配置顺序尝试各源，任一成功即返回；
     * 全部失败抛异常并携带各源错误摘要。
     */
    private List<MarketCandle> fetchWithFailover(String pair, String freq, int limit)
            throws InterruptedException {
        return fetchPage(index(), pair, freq, limit, null, null, null);
    }

    /**
     * 分页取一页：支持指定源（forbidden 之外的优先源）与结束时间游标。
     *
     * @param byName     已注册源（name -> 源）
     * @param cursor     结束时间游标（秒，含），null 表示取最近
     * @param forcedSource 指定源（非空则只用该源，失败不切换）
     * @param preferred  优先源（故障切换时排在首位，用于跨页粘性复用）
     */
    private List<MarketCandle> fetchPage(Map<String, MarketDataSource> byName, String pair,
                                         String freq, int limit, Long cursor,
                                         String forcedSource, String preferred)
            throws InterruptedException {
        List<String> order = new ArrayList<>();
        if (forcedSource != null && !forcedSource.isBlank()) {
            order.add(forcedSource.trim());
        } else {
            List<String> configured = properties.getSources() == null || properties.getSources().isEmpty()
                    ? new ArrayList<>(byName.keySet()) : properties.getSources();
            if (preferred != null && byName.containsKey(preferred)) {
                order.add(preferred);
            }
            for (String name : configured) {
                if (!order.contains(name)) {
                    order.add(name);
                }
            }
        }
        int attempts = Math.max(1, properties.getMaxAttempts());
        List<String> errors = new ArrayList<>();

        for (String name : order) {
            MarketDataSource source = byName.get(name);
            if (source == null) {
                errors.add(name + ": 未注册");
                continue;
            }
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    List<MarketCandle> candles = source.fetch(pair, freq, limit, cursor);
                    if (candles == null || candles.isEmpty()) {
                        throw new IllegalStateException("无数据");
                    }
                    lastSource = name;
                    markSource(name, "OK", null);
                    return candles;
                } catch (Exception error) {
                    markSource(name, "ERROR", error.getMessage());
                    errors.add(name + "(#" + attempt + "): " + error.getMessage());
                    log.warn("行情源 {} 第 {}/{} 次失败: {}", name, attempt, attempts,
                            error.getMessage());
                    if (attempt < attempts) {
                        Thread.sleep(Math.max(0, properties.getRetryBackoffMs()));
                    }
                }
            }
        }
        throw new IllegalStateException("全部行情源不可用 -> " + String.join(" | ", errors));
    }

    private Map<String, MarketDataSource> index() {
        Map<String, MarketDataSource> byName = new LinkedHashMap<>();
        for (MarketDataSource source : sources) {
            byName.put(source.name(), source);
        }
        return byName;
    }

    private void markSource(String name, String status, String error) {
        Map<String, Object> state = sourceState.computeIfAbsent(name, key -> new ConcurrentHashMap<>());
        state.put("status", status);
        state.put("lastError", error == null ? "" : error); // ConcurrentHashMap 不允许 null 值
        if ("OK".equals(status)) {
            state.put("lastSuccessAt",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("sources", properties.getSources());
        result.put("maxAttempts", properties.getMaxAttempts());
        result.put("timeoutSeconds", properties.getTimeoutSeconds());
        result.put("currentSource", lastSource == null ? "（尚未刷新）" : lastSource);
        result.put("sourceState", sourceState);
        result.put("autoRefreshMs", properties.getRefreshIntervalMs());
        result.put("lastRefreshAt", lastRefreshAt == null ? "（尚未刷新）"
                : lastRefreshAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("lastInserted", lastRefreshed);
        return result;
    }

    private Set<LocalDateTime> loadExisting(String symbol, String frequency) {
        Set<LocalDateTime> times = new HashSet<>();
        try {
            List<BarData> bars = repository.queryBars(
                    symbol, frequency, "2000-01-01T00:00:00", "2099-12-31T23:59:59", 1000);
            for (BarData bar : bars) {
                times.add(bar.getBarTime());
            }
        } catch (Exception ignored) {
            // 查询失败时按无已存在处理
        }
        return times;
    }

    /** 回补场景：按待写入 K 线的时间范围查询既存时间戳（跨 >1000 根也能去重）。 */
    private Set<LocalDateTime> loadExisting(String symbol, String frequency,
                                            List<MarketCandle> candles) {
        Set<LocalDateTime> times = new HashSet<>();
        if (candles == null || candles.isEmpty()) {
            return times;
        }
        long earliest = candles.get(0).timestamp();
        long latest = candles.get(candles.size() - 1).timestamp();
        String start = toBarTime(earliest - 86_400).format(TS_FORMAT);
        String end = toBarTime(latest + 86_400).format(TS_FORMAT);
        int limit = Math.max(1000, candles.size() + 100);
        try {
            List<BarData> bars = repository.queryBars(symbol, frequency, start, end, limit);
            for (BarData bar : bars) {
                times.add(bar.getBarTime());
            }
        } catch (Exception ignored) {
            // 查询失败时按无已存在处理
        }
        return times;
    }

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static LocalDateTime toBarTime(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static BigDecimal dec(String value) {
        try {
            return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
        } catch (NumberFormatException error) {
            return BigDecimal.ZERO;
        }
    }

    private static long toLong(String value) {
        try {
            return value == null || value.isBlank() ? 0L : (long) Double.parseDouble(value);
        } catch (NumberFormatException error) {
            return 0L;
        }
    }
}