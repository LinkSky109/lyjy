package com.lhjypt.core.model.backtest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 回测结果
 */
public class BacktestResult {

    /** 回测ID */
    private String backtestId;

    /** 策略名称 */
    private String strategyName;

    /** 回测状态：RUNNING/COMPLETED/FAILED */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    private long durationMs;

    /** 绩效指标 */
    private PerformanceMetrics metrics;

    /** 交易记录 */
    private List<TradeRecord> trades = new ArrayList<>();

    /** 持仓快照（净值曲线） */
    private List<PositionSnapshot> snapshots = new ArrayList<>();

    /** 未来函数检测警告 */
    private List<String> lookaheadWarnings = new ArrayList<>();

    /** 错误信息 */
    private String errorMessage;

    public String getBacktestId() {
        return backtestId;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public PerformanceMetrics getMetrics() {
        return metrics;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<TradeRecord> getTrades() {
        return trades;
    }

    public List<PositionSnapshot> getSnapshots() {
        return snapshots;
    }

    public List<String> getLookaheadWarnings() {
        return lookaheadWarnings;
    }

    public void setBacktestId(String backtestId) {
        this.backtestId = backtestId;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public void setMetrics(PerformanceMetrics metrics) {
        this.metrics = metrics;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setTrades(List<TradeRecord> trades) {
        this.trades = trades;
    }

    public void setSnapshots(List<PositionSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    public void setLookaheadWarnings(List<String> lookaheadWarnings) {
        this.lookaheadWarnings = lookaheadWarnings;
    }

    @Override
    public String toString() {
        return "BacktestResult{backtestId=" + backtestId + ", strategyName=" + strategyName + ", status=" + status + ", startTime=" + startTime + ", endTime=" + endTime + ", durationMs=" + durationMs + ", metrics=" + metrics + ", errorMessage=" + errorMessage + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BacktestResult that = (BacktestResult) o;
        return Objects.equals(backtestId, that.backtestId)
                && Objects.equals(strategyName, that.strategyName)
                && Objects.equals(status, that.status)
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime)
                && Objects.equals(durationMs, that.durationMs)
                && Objects.equals(metrics, that.metrics)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backtestId, strategyName, status, startTime, endTime, durationMs, metrics, errorMessage);
    }
}
