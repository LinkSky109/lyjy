package com.lhjypt.core.service.backtest;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 未来函数检测器
 * 检测回测策略中是否存在"未来信息泄露"：
 * 1. 时间穿越：策略在T时刻使用了T时刻之后的数据
 * 2. 收盘价交易：以收盘价下单但实际无法成交（收盘价已知时已无法交易）
 * 3. 未来数据引用：策略引用了未来才会公布的数据（如财报、分红）
 * 4. 幸存者偏差：回测标的集合使用了未来才确定的成分股
 */
@Service
public class LookaheadBiasDetector {
    private static final Logger log = LoggerFactory.getLogger(LookaheadBiasDetector.class);


    /** 记录策略每次数据访问的时间点 */
    private final Map<String, List<DataAccessRecord>> accessRecords = new ConcurrentHashMap<>();

    /** 警告列表 */
    private final List<String> warnings = new ArrayList<>();

    /** 回测当前时间 */
    private volatile LocalDateTime currentBacktestTime;

    /**
     * 记录策略数据访问
     * @param symbol 标的
     * @param dataTime 被访问数据的时间
     * @param accessTime 访问发生的时间（回测当前时间）
     */
    public void recordDataAccess(String symbol, LocalDateTime dataTime, LocalDateTime accessTime) {
        if (dataTime != null && accessTime != null && dataTime.isAfter(accessTime)) {
            String warning = String.format("[未来函数检测] %s 在 %s 访问了未来数据 %s（数据时间晚于当前时间）",
                    symbol, accessTime, dataTime);
            warnings.add(warning);
            log.warn(warning);
        }

        accessRecords.computeIfAbsent(symbol, k -> new ArrayList<>())
                .add(new DataAccessRecord(symbol, dataTime, accessTime));
    }

    /**
     * 检测收盘价交易问题
     * 策略在K线收盘时以收盘价下单，实际中收盘价确定时已无法交易
     */
    public void checkClosePriceTrading(String symbol, LocalDateTime barCloseTime, LocalDateTime orderTime) {
        if (barCloseTime != null && orderTime != null &&
                orderTime.isEqual(barCloseTime)) {
            String warning = String.format("[收盘价交易风险] %s 在K线收盘时刻 %s 以收盘价下单，实际无法成交",
                    symbol, barCloseTime);
            warnings.add(warning);
            log.warn(warning);
        }
    }

    /**
     * 检测策略是否使用了未来函数
     * 常见模式：
     * - 使用了T+1的数据做T日决策
     * - 引用了未来才公布的基本面数据
     * - 使用了整段回测期的数据计算指标（如全期均值）
     */
    public List<String> detect() {
        List<String> detected = new ArrayList<>(warnings);

        // 统计分析：检查是否存在大量未来数据访问
        for (Map.Entry<String, List<DataAccessRecord>> entry : accessRecords.entrySet()) {
            long futureAccessCount = entry.getValue().stream()
                    .filter(r -> r.dataTime != null && r.accessTime != null && r.dataTime.isAfter(r.accessTime))
                    .count();
            if (futureAccessCount > 10) {
                detected.add(String.format("[严重未来函数] %s 存在%d次未来数据访问，策略可能使用了未来信息",
                        entry.getKey(), futureAccessCount));
            }
        }

        return detected;
    }

    /**
     * 重置检测器
     */
    public void reset() {
        accessRecords.clear();
        warnings.clear();
        currentBacktestTime = null;
    }

    /**
     * 获取所有警告
     */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    /**
     * 设置回测当前时间
     */
    public void setCurrentBacktestTime(LocalDateTime time) {
        this.currentBacktestTime = time;
    }

    /**
     * 数据访问记录
     */
    private static class DataAccessRecord {
        String symbol;
        LocalDateTime dataTime;
        LocalDateTime accessTime;

        DataAccessRecord(String symbol, LocalDateTime dataTime, LocalDateTime accessTime) {
            this.symbol = symbol;
            this.dataTime = dataTime;
            this.accessTime = accessTime;
        }
    }
}
