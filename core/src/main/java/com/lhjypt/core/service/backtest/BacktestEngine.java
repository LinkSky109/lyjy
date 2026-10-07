package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.backtest.*;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.backtest.impl.TickMatchingEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 回测引擎核心
 * 事件驱动的tick级回测框架：
 * 1. 加载历史行情数据
 * 2. 按时间顺序逐个tick推进
 * 3. 每个tick：更新持仓估值 → 撮合挂单 → 调用策略on_tick → 记录快照
 * 4. 回测结束后计算绩效指标
 */
@Service
@RequiredArgsConstructor
public class BacktestEngine {
    private static final Logger log = LoggerFactory.getLogger(BacktestEngine.class);


    private final TickMatchingEngine matchingEngine;
    private final PerformanceCalculator performanceCalculator;
    private final LookaheadBiasDetector lookaheadDetector;

    /** 运行中的回测任务 */
    private final Map<String, BacktestResult> runningBacktests = new ConcurrentHashMap<>();

    /**
     * 运行回测
     * @param config 回测配置
     * @param ticks 历史行情数据（按时间排序）
     * @return 回测结果
     */
    public BacktestResult run(BacktestConfig config, List<TickData> ticks) {
        String backtestId = "BT-" + UUID.randomUUID().toString().substring(0, 8);
        config.setBacktestId(backtestId);

        BacktestResult result = new BacktestResult();
        result.setBacktestId(backtestId);
        result.setStrategyName(config.getStrategyName());
        result.setStatus("RUNNING");
        result.setStartTime(LocalDateTime.now());

        long startMs = System.currentTimeMillis();

        try {
            // 1. 初始化
            matchingEngine.init(config);
            if (config.isLookaheadDetectionEnabled()) {
                lookaheadDetector.reset();
            }

            // 2. 初始化账户
            BigDecimal cash = config.getInitialCapital();
            Map<String, BigDecimal> positions = new ConcurrentHashMap<>();
            BigDecimal realizedPnl = BigDecimal.ZERO;
            BigDecimal totalCommission = BigDecimal.ZERO;
            int tradeCount = 0;

            // 内置示例策略信号源（外部策略脚本接入前用于闭环自检）
            BuiltInStrategy strategy = new BuiltInStrategy();

            // 3. 逐tick回测
            for (TickData tick : ticks) {
                if (config.isLookaheadDetectionEnabled()) {
                    lookaheadDetector.setCurrentBacktestTime(tick.getTickTime());
                }

                // 3.1 撮合挂单
                strategy.onTick(tick, matchingEngine);
            List<TradeRecord> trades = matchingEngine.processTick(tick);
                for (TradeRecord trade : trades) {
                    // 更新资金和持仓
                    if ("BUY".equals(trade.getDirection())) {
                        cash = cash.subtract(trade.getTurnover()).subtract(trade.getTotalCost());
                        positions.merge(trade.getSymbol(), trade.getQuantity(), BigDecimal::add);
                    } else if ("SELL".equals(trade.getDirection())) {
                        cash = cash.add(trade.getTurnover()).subtract(trade.getTotalCost());
                        positions.merge(trade.getSymbol(), trade.getQuantity().negate(), BigDecimal::add);
                    }
                    totalCommission = totalCommission.add(trade.getCommission());
                    realizedPnl = realizedPnl.subtract(trade.getTotalCost());
                    tradeCount++;
                    result.getTrades().add(trade);
                }

                // 3.2 计算持仓市值
                BigDecimal positionValue = BigDecimal.ZERO;
                for (Map.Entry<String, BigDecimal> entry : positions.entrySet()) {
                    if (entry.getKey().equals(tick.getSymbol()) && tick.getLastPrice() != null) {
                        positionValue = positionValue.add(entry.getValue().multiply(tick.getLastPrice()));
                    }
                }

                // 3.3 记录快照
                PositionSnapshot snapshot = new PositionSnapshot();
                snapshot.setSnapshotTime(tick.getTickTime());
                snapshot.setAvailableCash(cash);
                snapshot.setPositionValue(positionValue);
                snapshot.setTotalAsset(cash.add(positionValue));
                snapshot.setRealizedPnl(realizedPnl);
                snapshot.setTotalCommission(totalCommission);
                snapshot.setTradeCount(tradeCount);
                snapshot.setPositionCount((int) positions.values().stream().filter(v -> v.compareTo(BigDecimal.ZERO) != 0).count());
                result.getSnapshots().add(snapshot);
            }

            // 4. 未来函数检测
            if (config.isLookaheadDetectionEnabled()) {
                result.setLookaheadWarnings(lookaheadDetector.detect());
            }

            // 5. 计算绩效
            PerformanceMetrics metrics = performanceCalculator.calculate(
                    result.getSnapshots(), result.getTrades(), config.getInitialCapital());
            result.setMetrics(metrics);

            result.setStatus("COMPLETED");

        } catch (Exception e) {
            log.error("回测失败: {}", e.getMessage(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
        }

        result.setEndTime(LocalDateTime.now());
        result.setDurationMs(System.currentTimeMillis() - startMs);
        log.info("回测完成: {} 状态:{} 耗时:{}ms 交易数:{}",
                backtestId, result.getStatus(), result.getDurationMs(), result.getTrades().size());

        return result;
    }

    /**
     * 异步运行回测（长时间任务）
     */
    public String runAsync(BacktestConfig config, List<TickData> ticks) {
        String backtestId = "BT-" + UUID.randomUUID().toString().substring(0, 8);
        config.setBacktestId(backtestId);

        BacktestResult placeholder = new BacktestResult();
        placeholder.setBacktestId(backtestId);
        placeholder.setStatus("RUNNING");
        runningBacktests.put(backtestId, placeholder);

        new Thread(() -> {
            BacktestResult result = run(config, ticks);
            runningBacktests.put(backtestId, result);
        }, "backtest-" + backtestId).start();

        return backtestId;
    }

    /**
     * 查询回测结果
     */
    public BacktestResult getResult(String backtestId) {
        return runningBacktests.get(backtestId);
    }

    /**
     * 生成模拟行情数据（用于无真实数据时的回测演示）
     */
    public List<TickData> generateMockTicks(String symbol, int count, BigDecimal basePrice) {
        List<TickData> ticks = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusHours(count);
        // 固定小数位：随机游走若用 BigDecimal 反复乘(1+change)会导致 scale 无限膨胀，
        // 使运算退化为 BigInteger.pow 超大整数乘法（10000 ticks 时卡死）。
        BigDecimal price = basePrice.setScale(8, RoundingMode.HALF_UP);
        BigDecimal volumeFactor = BigDecimal.valueOf(1.001d);
        BigDecimal priceDown = BigDecimal.valueOf(0.999d);

        for (int i = 0; i < count; i++) {
            TickData tick = new TickData();
            tick.setSymbol(symbol);
            // 随机游走（double 运算后按固定精度落盘，scale 恒为 8）
            double change = (Math.random() - 0.5) * 0.002;
            price = BigDecimal.valueOf(
                    price.doubleValue() * (1 + change)).setScale(8, RoundingMode.HALF_UP);
            BigDecimal high = price.multiply(volumeFactor).setScale(8, RoundingMode.HALF_UP);
            BigDecimal low = price.multiply(priceDown).setScale(8, RoundingMode.HALF_UP);
            long volume = (long) (Math.random() * 1000);
            tick.setLastPrice(price);
            tick.setPreClose(basePrice);
            tick.setOpen(price);
            tick.setHigh(high);
            tick.setLow(low);
            tick.setVolume(volume);
            tick.setTurnover(price.multiply(BigDecimal.valueOf(volume))
                    .setScale(8, RoundingMode.HALF_UP));
            tick.setBidPrice1(price.subtract(BigDecimal.valueOf(0.01)));
            tick.setAskPrice1(price.add(BigDecimal.valueOf(0.01)));
            tick.setBidVolume1((long) (Math.random() * 500));
            tick.setAskVolume1((long) (Math.random() * 500));
            tick.setTickTime(time.plusMinutes(i));
            tick.setRecvTime(time.plusMinutes(i));
            ticks.add(tick);
        }
        return ticks;
    }
}
