package com.lhjypt.core.model.backtest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 回测配置
 */
public class BacktestConfig {

    /** 回测ID */
    private String backtestId;

    /** 策略名称 */
    private String strategyName;

    /** 策略代码（Python脚本内容或路径） */
    private String strategyCode;

    /** 回测标的列表 */
    private List<String> symbols;

    /** 回测开始时间 */
    private LocalDateTime startTime;

    /** 回测结束时间 */
    private LocalDateTime endTime;

    /** 初始资金 */
    private BigDecimal initialCapital = new BigDecimal("1000000");

    /** 手续费率（单边） */
    private BigDecimal commissionRate = new BigDecimal("0.0003");

    /** 印花税率（卖出，股票） */
    private BigDecimal stampDutyRate = new BigDecimal("0.001");

    /** 滑点（绝对价格） */
    private BigDecimal slippage = new BigDecimal("0.01");

    /** 滑点模式：FIXED(固定)/PERCENT(百分比)/VOLUME(成交量比例) */
    private String slippageMode = "FIXED";

    /** 是否启用冲击成本模型 */
    private boolean impactCostEnabled = false;

    /** 撮合模式：TICK(逐笔)/BAR(收盘价) */
    private String matchingMode = "TICK";

    /** 是否启用未来函数检测 */
    private boolean lookaheadDetectionEnabled = true;

    /** 数据频率：tick/1m/5m/1d */
    private String dataFrequency = "1m";

    /** 杠杆倍数 */
    private BigDecimal leverage = BigDecimal.ONE;

    /** 备注 */
    private String remark;

    public String getBacktestId() {
        return backtestId;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public String getStrategyCode() {
        return strategyCode;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getRemark() {
        return remark;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public BigDecimal getStampDutyRate() {
        return stampDutyRate;
    }

    public BigDecimal getSlippage() {
        return slippage;
    }

    public String getSlippageMode() {
        return slippageMode;
    }

    public boolean isImpactCostEnabled() {
        return impactCostEnabled;
    }

    public String getMatchingMode() {
        return matchingMode;
    }

    public boolean isLookaheadDetectionEnabled() {
        return lookaheadDetectionEnabled;
    }

    public String getDataFrequency() {
        return dataFrequency;
    }

    public BigDecimal getLeverage() {
        return leverage;
    }

    public void setBacktestId(String backtestId) {
        this.backtestId = backtestId;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public void setStrategyCode(String strategyCode) {
        this.strategyCode = strategyCode;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public void setInitialCapital(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public void setStampDutyRate(BigDecimal stampDutyRate) {
        this.stampDutyRate = stampDutyRate;
    }

    public void setSlippage(BigDecimal slippage) {
        this.slippage = slippage;
    }

    public void setSlippageMode(String slippageMode) {
        this.slippageMode = slippageMode;
    }

    public void setImpactCostEnabled(boolean impactCostEnabled) {
        this.impactCostEnabled = impactCostEnabled;
    }

    public void setMatchingMode(String matchingMode) {
        this.matchingMode = matchingMode;
    }

    public void setLookaheadDetectionEnabled(boolean lookaheadDetectionEnabled) {
        this.lookaheadDetectionEnabled = lookaheadDetectionEnabled;
    }

    public void setDataFrequency(String dataFrequency) {
        this.dataFrequency = dataFrequency;
    }

    public void setLeverage(BigDecimal leverage) {
        this.leverage = leverage;
    }

    @Override
    public String toString() {
        return "BacktestConfig{backtestId=" + backtestId + ", strategyName=" + strategyName + ", strategyCode=" + strategyCode + ", symbols=" + symbols + ", startTime=" + startTime + ", endTime=" + endTime + ", remark=" + remark + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BacktestConfig that = (BacktestConfig) o;
        return Objects.equals(backtestId, that.backtestId)
                && Objects.equals(strategyName, that.strategyName)
                && Objects.equals(strategyCode, that.strategyCode)
                && Objects.equals(symbols, that.symbols)
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime)
                && Objects.equals(remark, that.remark);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backtestId, strategyName, strategyCode, symbols, startTime, endTime, remark);
    }
}
