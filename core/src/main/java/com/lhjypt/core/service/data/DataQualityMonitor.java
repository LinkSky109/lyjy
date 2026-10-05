package com.lhjypt.core.service.data;

import com.lhjypt.core.model.data.DataQualityStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据质量监控服务
 * 实时监控各标的行情数据质量，统计异常率，超阈值告警
 */
@Service
public class DataQualityMonitor {
    private static final Logger log = LoggerFactory.getLogger(DataQualityMonitor.class);


    @Value("${lhjypt.data-quality.alert-dirty-rate:5.0}")
    private double alertDirtyRate;

    @Value("${lhjypt.data-quality.alert-delay-seconds:30}")
    private int alertDelaySeconds;

    private final Map<String, DataQualityStats> statsMap = new ConcurrentHashMap<>();

    public void recordReceived(String symbol) {
        getStats(symbol).getTotalReceived().incrementAndGet();
        getStats(symbol).setLastRecvTime(LocalDateTime.now());
    }

    public void recordClean(String symbol) {
        getStats(symbol).getCleanCount().incrementAndGet();
        getStats(symbol).setLastCleanTime(LocalDateTime.now());
    }

    public void recordDirty(String symbol, String errorType) {
        getStats(symbol).getDirtyCount().incrementAndGet();
    }

    public void recordMissing(String symbol) {
        getStats(symbol).getMissingCount().incrementAndGet();
    }

    public void recordPriceJump(String symbol) {
        getStats(symbol).getPriceJumpCount().incrementAndGet();
    }

    public void recordZeroValue(String symbol) {
        getStats(symbol).getZeroValueCount().incrementAndGet();
    }

    /**
     * 获取指定标的的质量统计
     */
    public DataQualityStats getStats(String symbol) {
        return statsMap.computeIfAbsent(symbol, k -> {
            DataQualityStats stats = new DataQualityStats();
            stats.setSymbol(k);
            return stats;
        });
    }

    /**
     * 获取所有标的的质量统计
     */
    public Map<String, DataQualityStats> getAllStats() {
        return statsMap;
    }

    /**
     * 检查数据延迟（超过阈值无数据则告警）
     */
    public boolean isDelayed(String symbol) {
        DataQualityStats stats = statsMap.get(symbol);
        if (stats == null || stats.getLastRecvTime() == null) return true;
        return stats.getLastRecvTime().plusSeconds(alertDelaySeconds).isBefore(LocalDateTime.now());
    }

    /**
     * 检查异常率是否超阈值
     */
    public boolean isDirtyRateExceeded(String symbol) {
        DataQualityStats stats = statsMap.get(symbol);
        if (stats == null) return false;
        return stats.getDirtyRate() > alertDirtyRate;
    }

    /**
     * 定时巡检数据质量（每分钟）
     */
    @Scheduled(fixedRate = 60000)
    public void scheduledCheck() {
        for (Map.Entry<String, DataQualityStats> entry : statsMap.entrySet()) {
            String symbol = entry.getKey();
            DataQualityStats stats = entry.getValue();

            if (isDelayed(symbol)) {
                log.warn("[数据质量告警] {} 行情延迟超过{}秒，最后接收: {}",
                        symbol, alertDelaySeconds, stats.getLastRecvTime());
            }
            if (isDirtyRateExceeded(symbol)) {
                log.warn("[数据质量告警] {} 异常率{:.2f}%超过阈值{}%，总接收:{}, 异常:{}",
                        symbol, stats.getDirtyRate(), alertDirtyRate,
                        stats.getTotalReceived().get(), stats.getDirtyCount().get());
            }
        }
    }

    /**
     * 重置指定标的统计
     */
    public void reset(String symbol) {
        DataQualityStats stats = statsMap.get(symbol);
        if (stats != null) {
            stats.reset();
        }
    }

    /**
     * 重置所有统计
     */
    public void resetAll() {
        statsMap.values().forEach(DataQualityStats::reset);
    }
}
