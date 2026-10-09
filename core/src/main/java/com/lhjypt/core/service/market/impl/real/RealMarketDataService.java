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
            int inserted = 0;
            for (MarketCandle candle : candles) {
                LocalDateTime barTime = Instant.ofEpochSecond(candle.timestamp())
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
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
                inserted++;
            }
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
     * 多源故障切换 + 每源重试：按配置顺序尝试各源，任一成功即返回；
     * 全部失败抛异常并携带各源错误摘要。
     */
    private List<MarketCandle> fetchWithFailover(String pair, String freq, int limit)
            throws InterruptedException {
        Map<String, MarketDataSource> byName = new LinkedHashMap<>();
        for (MarketDataSource source : sources) {
            byName.put(source.name(), source);
        }
        List<String> order = properties.getSources() == null || properties.getSources().isEmpty()
                ? new ArrayList<>(byName.keySet()) : properties.getSources();
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
                    List<MarketCandle> candles = source.fetch(pair, freq, limit);
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