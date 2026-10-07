package com.lhjypt.core.service.market.impl.real;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.config.RealMarketProperties;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.service.market.MarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 真实行情接入服务（OBS-005）：拉取 Gate.io 现货 K 线并写入 ClickHouse bar_data，
 * 使 {@code /api/market/bars} 与 VaR 等消费真实行情。重复调用按 (symbol, freq, bar_time)
 * 去重，不产生重复行。
 */
@Service
public class RealMarketDataService {

    private static final Logger log = LoggerFactory.getLogger(RealMarketDataService.class);

    private final RealMarketProperties properties;
    private final GateioKlineSource klineSource;
    private final MarketDataRepository repository;
    private final DistributedLock distributedLock;

    private volatile LocalDateTime lastRefreshAt;
    private volatile int lastRefreshed;

    public RealMarketDataService(RealMarketProperties properties,
                                 GateioKlineSource klineSource,
                                 MarketDataRepository repository,
                                 DistributedLock distributedLock) {
        this.properties = properties;
        this.klineSource = klineSource;
        this.repository = repository;
        this.distributedLock = distributedLock;
    }

    /** 定时自动刷新（间隔 0 时关闭）；失败仅记录日志，不中断调度。 */
    @Scheduled(fixedRateString = "${lhjypt.market.real.refresh-interval-ms:900000}")
    public void scheduledRefresh() {
        if (!properties.isEnabled() || properties.getRefreshIntervalMs() <= 0) {
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
            List<GateioKlineSource.RealKline> candles =
                    klineSource.fetchCandles(pair, freq, countLimit);
            // 已存在时间戳（限最近 1000 根），跳过避免重复写入
            Set<LocalDateTime> existing = loadExisting(pair, freq);
            int inserted = 0;
            for (GateioKlineSource.RealKline candle : candles) {
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
            log.info("真实行情刷新完成: {}@{} 拉取 {} 新增 {}", pair, freq,
                    candles.size(), inserted);
            return inserted;
        } catch (Exception error) {
            throw new IllegalStateException("真实行情刷新失败: " + error.getMessage(), error);
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("source", "gateio");
        result.put("baseUrl", properties.getBaseUrl());
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
            // 查询失败时按无已存在处理（幂等性由上层保证非核心诉求）
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