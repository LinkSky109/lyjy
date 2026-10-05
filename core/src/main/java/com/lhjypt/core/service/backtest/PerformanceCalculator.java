package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.backtest.PerformanceMetrics;
import com.lhjypt.core.model.backtest.PositionSnapshot;
import com.lhjypt.core.model.backtest.TradeRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 绩效计算器
 * 计算回测结果的各项绩效指标
 */
@Service
public class PerformanceCalculator {

    /** 无风险利率（年化3%） */
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.03");

    /** 一年交易日 */
    private static final int TRADING_DAYS = 252;

    /**
     * 计算完整绩效指标
     */
    public PerformanceMetrics calculate(List<PositionSnapshot> snapshots, List<TradeRecord> trades,
                                        BigDecimal initialCapital) {
        PerformanceMetrics metrics = new PerformanceMetrics();

        if (snapshots == null || snapshots.isEmpty()) {
            return metrics;
        }

        // 回测天数
        long days = ChronoUnit.DAYS.between(
                snapshots.get(0).getSnapshotTime(),
                snapshots.get(snapshots.size() - 1).getSnapshotTime());
        metrics.setBacktestDays(days);

        // 1. 收益率
        BigDecimal finalAsset = snapshots.get(snapshots.size() - 1).getTotalAsset();
        BigDecimal totalReturn = finalAsset.subtract(initialCapital)
                .divide(initialCapital, 8, RoundingMode.HALF_UP);
        metrics.setTotalReturn(totalReturn);

        // 年化收益率
        if (days > 0) {
            BigDecimal years = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP);
            if (years.compareTo(BigDecimal.ZERO) > 0) {
                // 年化 = (1+总收益)^(1/年数) - 1
                double annualized = Math.pow(1 + totalReturn.doubleValue(), 1 / years.doubleValue()) - 1;
                metrics.setAnnualizedReturn(BigDecimal.valueOf(annualized).setScale(8, RoundingMode.HALF_UP));
            }
        }

        // 2. 日收益率序列
        List<BigDecimal> dailyReturns = calculateDailyReturns(snapshots);

        // 3. 波动率
        BigDecimal volatility = calculateVolatility(dailyReturns);
        metrics.setAnnualizedVolatility(volatility);

        // 4. 最大回撤
        calculateMaxDrawdown(snapshots, metrics);

        // 5. 夏普比率
        if (volatility.compareTo(BigDecimal.ZERO) > 0 && metrics.getAnnualizedReturn() != null) {
            BigDecimal excessReturn = metrics.getAnnualizedReturn().subtract(RISK_FREE_RATE);
            BigDecimal sharpe = excessReturn.divide(volatility, 4, RoundingMode.HALF_UP);
            metrics.setSharpeRatio(sharpe);
        }

        // 6. 下行波动率 & 索提诺比率
        BigDecimal downsideVol = calculateDownsideVolatility(dailyReturns);
        metrics.setDownsideVolatility(downsideVol);
        if (downsideVol.compareTo(BigDecimal.ZERO) > 0 && metrics.getAnnualizedReturn() != null) {
            BigDecimal sortino = metrics.getAnnualizedReturn().subtract(RISK_FREE_RATE)
                    .divide(downsideVol, 4, RoundingMode.HALF_UP);
            metrics.setSortinoRatio(sortino);
        }

        // 7. 卡玛比率
        if (metrics.getMaxDrawdown() != null && metrics.getMaxDrawdown().compareTo(BigDecimal.ZERO) > 0
                && metrics.getAnnualizedReturn() != null) {
            BigDecimal calmar = metrics.getAnnualizedReturn()
                    .divide(metrics.getMaxDrawdown().abs(), 4, RoundingMode.HALF_UP);
            metrics.setCalmarRatio(calmar);
        }

        // 8. 交易统计
        calculateTradeStats(trades, metrics);

        return metrics;
    }

    private List<BigDecimal> calculateDailyReturns(List<PositionSnapshot> snapshots) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < snapshots.size(); i++) {
            BigDecimal prev = snapshots.get(i - 1).getTotalAsset();
            BigDecimal curr = snapshots.get(i).getTotalAsset();
            if (prev.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ret = curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP);
                returns.add(ret);
            }
        }
        return returns;
    }

    private BigDecimal calculateVolatility(List<BigDecimal> returns) {
        if (returns.size() < 2) return BigDecimal.ZERO;
        double mean = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r.doubleValue() - mean, 2))
                .average().orElse(0);
        double dailyVol = Math.sqrt(variance);
        // 年化
        return BigDecimal.valueOf(dailyVol * Math.sqrt(TRADING_DAYS))
                .setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDownsideVolatility(List<BigDecimal> returns) {
        List<BigDecimal> negativeReturns = new ArrayList<>();
        for (BigDecimal r : returns) {
            if (r.compareTo(BigDecimal.ZERO) < 0) {
                negativeReturns.add(r);
            }
        }
        if (negativeReturns.isEmpty()) return BigDecimal.ZERO;
        double mean = negativeReturns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = negativeReturns.stream()
                .mapToDouble(r -> Math.pow(r.doubleValue() - mean, 2))
                .average().orElse(0);
        return BigDecimal.valueOf(Math.sqrt(variance) * Math.sqrt(TRADING_DAYS))
                .setScale(8, RoundingMode.HALF_UP);
    }

    private void calculateMaxDrawdown(List<PositionSnapshot> snapshots, PerformanceMetrics metrics) {
        BigDecimal peak = snapshots.get(0).getTotalAsset();
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        String peakTime = snapshots.get(0).getSnapshotTime().toString();
        String troughTime = peakTime;
        String currentPeakTime = peakTime;

        for (PositionSnapshot snap : snapshots) {
            if (snap.getTotalAsset().compareTo(peak) > 0) {
                peak = snap.getTotalAsset();
                currentPeakTime = snap.getSnapshotTime().toString();
            }
            BigDecimal drawdown = peak.subtract(snap.getTotalAsset())
                    .divide(peak, 8, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
                peakTime = currentPeakTime;
                troughTime = snap.getSnapshotTime().toString();
            }
        }

        metrics.setMaxDrawdown(maxDrawdown);
        metrics.setMaxDrawdownStart(peakTime);
        metrics.setMaxDrawdownEnd(troughTime);
    }

    private void calculateTradeStats(List<TradeRecord> trades, PerformanceMetrics metrics) {
        if (trades == null || trades.isEmpty()) {
            metrics.setTotalTrades(0);
            metrics.setWinRate(BigDecimal.ZERO);
            return;
        }

        // 以"完整开平回合"为一笔交易：FIFO 将卖出平仓与此前买入开仓配对，
        // 计算每回合真实净盈亏（买卖价差 - 双边交易成本）。
        java.util.List<BigDecimal> roundTripPnls = new java.util.ArrayList<>();
        java.util.Deque<Lot> openLots = new java.util.ArrayDeque<>();

        for (TradeRecord trade : trades) {
            BigDecimal qty = trade.getQuantity();
            BigDecimal price = trade.getPrice();
            BigDecimal cost = trade.getTotalCost() == null ? BigDecimal.ZERO : trade.getTotalCost();
            if (qty == null || price == null) {
                continue;
            }

            if ("BUY".equals(trade.getDirection())) {
                // 开仓批次，记录单位成本以便后续分摊
                openLots.add(new Lot(price, qty, cost.divide(qty, 8, RoundingMode.HALF_UP)));
            } else if ("SELL".equals(trade.getDirection())) {
                // 与未平买入批次 FIFO 配对
                BigDecimal remaining = qty;
                BigDecimal pnl = cost.negate(); // 先扣本笔卖出成本
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && !openLots.isEmpty()) {
                    Lot lot = openLots.peekFirst();
                    BigDecimal matched = remaining.min(lot.remaining);
                    pnl = pnl.add(matched.multiply(price.subtract(lot.price)))
                            .subtract(matched.multiply(lot.unitCost));
                    lot.remaining = lot.remaining.subtract(matched);
                    remaining = remaining.subtract(matched);
                    if (lot.remaining.compareTo(BigDecimal.ZERO) == 0) {
                        openLots.pollFirst();
                    }
                }
                roundTripPnls.add(pnl);
            }
        }

        metrics.setTotalTrades(roundTripPnls.size());

        int wins = 0, losses = 0;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;
        int consecutiveLosses = 0, maxConsecutiveLosses = 0;
        int consecutiveWins = 0, maxConsecutiveWins = 0;

        for (BigDecimal pnl : roundTripPnls) {
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                wins++;
                totalProfit = totalProfit.add(pnl);
                consecutiveWins++;
                consecutiveLosses = 0;
                maxConsecutiveWins = Math.max(maxConsecutiveWins, consecutiveWins);
            } else {
                losses++;
                totalLoss = totalLoss.add(pnl);
                consecutiveLosses++;
                consecutiveWins = 0;
                maxConsecutiveLosses = Math.max(maxConsecutiveLosses, consecutiveLosses);
            }
        }

        metrics.setWinningTrades(wins);
        metrics.setLosingTrades(losses);
        if (!roundTripPnls.isEmpty()) {
            metrics.setWinRate(BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(roundTripPnls.size()), 4, RoundingMode.HALF_UP));
        }
        metrics.setMaxConsecutiveLosses(maxConsecutiveLosses);
        metrics.setMaxConsecutiveWins(maxConsecutiveWins);
        metrics.setAvgProfit(wins > 0 ? totalProfit.divide(BigDecimal.valueOf(wins), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        metrics.setAvgLoss(losses > 0 ? totalLoss.divide(BigDecimal.valueOf(losses), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        if (losses > 0 && metrics.getAvgLoss().compareTo(BigDecimal.ZERO) != 0) {
            metrics.setProfitLossRatio(metrics.getAvgProfit().divide(metrics.getAvgLoss().abs(), 4, RoundingMode.HALF_UP));
        }
    }

    /** 未平仓买入批次（FIFO 配对用） */
    private static class Lot {
        final BigDecimal price;
        BigDecimal remaining;
        final BigDecimal unitCost;

        Lot(BigDecimal price, BigDecimal quantity, BigDecimal unitCost) {
            this.price = price;
            this.remaining = quantity;
            this.unitCost = unitCost;
        }
    }
}
