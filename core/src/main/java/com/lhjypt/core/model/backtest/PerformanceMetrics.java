package com.lhjypt.core.model.backtest;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 绩效指标 - 回测结果统计
 */
public class PerformanceMetrics {

    // ===== 收益指标 =====
    /** 总收益率 */
    private BigDecimal totalReturn;

    /** 年化收益率 */
    private BigDecimal annualizedReturn;

    /** 基准收益率 */
    private BigDecimal benchmarkReturn;

    /** 超额收益 */
    private BigDecimal excessReturn;

    // ===== 风险指标 =====
    /** 年化波动率 */
    private BigDecimal annualizedVolatility;

    /** 最大回撤 */
    private BigDecimal maxDrawdown;

    /** 最大回撤开始时间 */
    private String maxDrawdownStart;

    /** 最大回撤结束时间 */
    private String maxDrawdownEnd;

    /** 下行波动率 */
    private BigDecimal downsideVolatility;

    // ===== 风险调整收益 =====
    /** 夏普比率（无风险利率3%） */
    private BigDecimal sharpeRatio;

    /** 索提诺比率 */
    private BigDecimal sortinoRatio;

    /** 卡玛比率（年化收益/最大回撤） */
    private BigDecimal calmarRatio;

    /** 信息比率 */
    private BigDecimal informationRatio;

    // ===== 交易统计 =====
    /** 总交易次数 */
    private int totalTrades;

    /** 盈利次数 */
    private int winningTrades;

    /** 亏损次数 */
    private int losingTrades;

    /** 胜率 */
    private BigDecimal winRate;

    /** 盈亏比 */
    private BigDecimal profitLossRatio;

    /** 平均盈利 */
    private BigDecimal avgProfit;

    /** 平均亏损 */
    private BigDecimal avgLoss;

    // ===== 其他 =====
    /** 最大连续亏损次数 */
    private int maxConsecutiveLosses;

    /** 最大连续盈利次数 */
    private int maxConsecutiveWins;

    /** 平均持仓周期（天） */
    private BigDecimal avgHoldingPeriod;

    /** 回测天数 */
    private long backtestDays;

    public BigDecimal getTotalReturn() {
        return totalReturn;
    }

    public BigDecimal getAnnualizedReturn() {
        return annualizedReturn;
    }

    public BigDecimal getBenchmarkReturn() {
        return benchmarkReturn;
    }

    public BigDecimal getExcessReturn() {
        return excessReturn;
    }

    public BigDecimal getAnnualizedVolatility() {
        return annualizedVolatility;
    }

    public BigDecimal getMaxDrawdown() {
        return maxDrawdown;
    }

    public String getMaxDrawdownStart() {
        return maxDrawdownStart;
    }

    public String getMaxDrawdownEnd() {
        return maxDrawdownEnd;
    }

    public BigDecimal getDownsideVolatility() {
        return downsideVolatility;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public BigDecimal getSortinoRatio() {
        return sortinoRatio;
    }

    public BigDecimal getCalmarRatio() {
        return calmarRatio;
    }

    public BigDecimal getInformationRatio() {
        return informationRatio;
    }

    public int getTotalTrades() {
        return totalTrades;
    }

    public int getWinningTrades() {
        return winningTrades;
    }

    public int getLosingTrades() {
        return losingTrades;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public BigDecimal getProfitLossRatio() {
        return profitLossRatio;
    }

    public BigDecimal getAvgProfit() {
        return avgProfit;
    }

    public BigDecimal getAvgLoss() {
        return avgLoss;
    }

    public int getMaxConsecutiveLosses() {
        return maxConsecutiveLosses;
    }

    public int getMaxConsecutiveWins() {
        return maxConsecutiveWins;
    }

    public BigDecimal getAvgHoldingPeriod() {
        return avgHoldingPeriod;
    }

    public long getBacktestDays() {
        return backtestDays;
    }

    public void setTotalReturn(BigDecimal totalReturn) {
        this.totalReturn = totalReturn;
    }

    public void setAnnualizedReturn(BigDecimal annualizedReturn) {
        this.annualizedReturn = annualizedReturn;
    }

    public void setBenchmarkReturn(BigDecimal benchmarkReturn) {
        this.benchmarkReturn = benchmarkReturn;
    }

    public void setExcessReturn(BigDecimal excessReturn) {
        this.excessReturn = excessReturn;
    }

    public void setAnnualizedVolatility(BigDecimal annualizedVolatility) {
        this.annualizedVolatility = annualizedVolatility;
    }

    public void setMaxDrawdown(BigDecimal maxDrawdown) {
        this.maxDrawdown = maxDrawdown;
    }

    public void setMaxDrawdownStart(String maxDrawdownStart) {
        this.maxDrawdownStart = maxDrawdownStart;
    }

    public void setMaxDrawdownEnd(String maxDrawdownEnd) {
        this.maxDrawdownEnd = maxDrawdownEnd;
    }

    public void setDownsideVolatility(BigDecimal downsideVolatility) {
        this.downsideVolatility = downsideVolatility;
    }

    public void setSharpeRatio(BigDecimal sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }

    public void setSortinoRatio(BigDecimal sortinoRatio) {
        this.sortinoRatio = sortinoRatio;
    }

    public void setCalmarRatio(BigDecimal calmarRatio) {
        this.calmarRatio = calmarRatio;
    }

    public void setInformationRatio(BigDecimal informationRatio) {
        this.informationRatio = informationRatio;
    }

    public void setTotalTrades(int totalTrades) {
        this.totalTrades = totalTrades;
    }

    public void setWinningTrades(int winningTrades) {
        this.winningTrades = winningTrades;
    }

    public void setLosingTrades(int losingTrades) {
        this.losingTrades = losingTrades;
    }

    public void setWinRate(BigDecimal winRate) {
        this.winRate = winRate;
    }

    public void setProfitLossRatio(BigDecimal profitLossRatio) {
        this.profitLossRatio = profitLossRatio;
    }

    public void setAvgProfit(BigDecimal avgProfit) {
        this.avgProfit = avgProfit;
    }

    public void setAvgLoss(BigDecimal avgLoss) {
        this.avgLoss = avgLoss;
    }

    public void setMaxConsecutiveLosses(int maxConsecutiveLosses) {
        this.maxConsecutiveLosses = maxConsecutiveLosses;
    }

    public void setMaxConsecutiveWins(int maxConsecutiveWins) {
        this.maxConsecutiveWins = maxConsecutiveWins;
    }

    public void setAvgHoldingPeriod(BigDecimal avgHoldingPeriod) {
        this.avgHoldingPeriod = avgHoldingPeriod;
    }

    public void setBacktestDays(long backtestDays) {
        this.backtestDays = backtestDays;
    }

    @Override
    public String toString() {
        return "PerformanceMetrics{totalReturn=" + totalReturn + ", annualizedReturn=" + annualizedReturn + ", benchmarkReturn=" + benchmarkReturn + ", excessReturn=" + excessReturn + ", annualizedVolatility=" + annualizedVolatility + ", maxDrawdown=" + maxDrawdown + ", maxDrawdownStart=" + maxDrawdownStart + ", maxDrawdownEnd=" + maxDrawdownEnd + ", downsideVolatility=" + downsideVolatility + ", sharpeRatio=" + sharpeRatio + ", sortinoRatio=" + sortinoRatio + ", calmarRatio=" + calmarRatio + ", informationRatio=" + informationRatio + ", totalTrades=" + totalTrades + ", winningTrades=" + winningTrades + ", losingTrades=" + losingTrades + ", winRate=" + winRate + ", profitLossRatio=" + profitLossRatio + ", avgProfit=" + avgProfit + ", avgLoss=" + avgLoss + ", maxConsecutiveLosses=" + maxConsecutiveLosses + ", maxConsecutiveWins=" + maxConsecutiveWins + ", avgHoldingPeriod=" + avgHoldingPeriod + ", backtestDays=" + backtestDays + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PerformanceMetrics that = (PerformanceMetrics) o;
        return Objects.equals(totalReturn, that.totalReturn)
                && Objects.equals(annualizedReturn, that.annualizedReturn)
                && Objects.equals(benchmarkReturn, that.benchmarkReturn)
                && Objects.equals(excessReturn, that.excessReturn)
                && Objects.equals(annualizedVolatility, that.annualizedVolatility)
                && Objects.equals(maxDrawdown, that.maxDrawdown)
                && Objects.equals(maxDrawdownStart, that.maxDrawdownStart)
                && Objects.equals(maxDrawdownEnd, that.maxDrawdownEnd)
                && Objects.equals(downsideVolatility, that.downsideVolatility)
                && Objects.equals(sharpeRatio, that.sharpeRatio)
                && Objects.equals(sortinoRatio, that.sortinoRatio)
                && Objects.equals(calmarRatio, that.calmarRatio)
                && Objects.equals(informationRatio, that.informationRatio)
                && Objects.equals(totalTrades, that.totalTrades)
                && Objects.equals(winningTrades, that.winningTrades)
                && Objects.equals(losingTrades, that.losingTrades)
                && Objects.equals(winRate, that.winRate)
                && Objects.equals(profitLossRatio, that.profitLossRatio)
                && Objects.equals(avgProfit, that.avgProfit)
                && Objects.equals(avgLoss, that.avgLoss)
                && Objects.equals(maxConsecutiveLosses, that.maxConsecutiveLosses)
                && Objects.equals(maxConsecutiveWins, that.maxConsecutiveWins)
                && Objects.equals(avgHoldingPeriod, that.avgHoldingPeriod)
                && Objects.equals(backtestDays, that.backtestDays);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalReturn, annualizedReturn, benchmarkReturn, excessReturn, annualizedVolatility, maxDrawdown, maxDrawdownStart, maxDrawdownEnd, downsideVolatility, sharpeRatio, sortinoRatio, calmarRatio, informationRatio, totalTrades, winningTrades, losingTrades, winRate, profitLossRatio, avgProfit, avgLoss, maxConsecutiveLosses, maxConsecutiveWins, avgHoldingPeriod, backtestDays);
    }
}
