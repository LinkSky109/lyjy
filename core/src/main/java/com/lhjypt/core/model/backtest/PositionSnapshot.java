package com.lhjypt.core.model.backtest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 持仓快照 - 回测过程中每个时间点的账户状态
 */
public class PositionSnapshot {

    /** 快照时间 */
    private LocalDateTime snapshotTime;

    /** 总资产 */
    private BigDecimal totalAsset;

    /** 可用资金 */
    private BigDecimal availableCash;

    /** 持仓市值 */
    private BigDecimal positionValue;

    /** 浮动盈亏 */
    private BigDecimal unrealizedPnl;

    /** 已实现盈亏 */
    private BigDecimal realizedPnl;

    /** 累计手续费 */
    private BigDecimal totalCommission;

    /** 累计交易次数 */
    private int tradeCount;

    /** 持仓标的数 */
    private int positionCount;

    public LocalDateTime getSnapshotTime() {
        return snapshotTime;
    }

    public BigDecimal getTotalAsset() {
        return totalAsset;
    }

    public BigDecimal getAvailableCash() {
        return availableCash;
    }

    public BigDecimal getPositionValue() {
        return positionValue;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public BigDecimal getTotalCommission() {
        return totalCommission;
    }

    public int getTradeCount() {
        return tradeCount;
    }

    public int getPositionCount() {
        return positionCount;
    }

    public void setSnapshotTime(LocalDateTime snapshotTime) {
        this.snapshotTime = snapshotTime;
    }

    public void setTotalAsset(BigDecimal totalAsset) {
        this.totalAsset = totalAsset;
    }

    public void setAvailableCash(BigDecimal availableCash) {
        this.availableCash = availableCash;
    }

    public void setPositionValue(BigDecimal positionValue) {
        this.positionValue = positionValue;
    }

    public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
        this.unrealizedPnl = unrealizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public void setTotalCommission(BigDecimal totalCommission) {
        this.totalCommission = totalCommission;
    }

    public void setTradeCount(int tradeCount) {
        this.tradeCount = tradeCount;
    }

    public void setPositionCount(int positionCount) {
        this.positionCount = positionCount;
    }

    @Override
    public String toString() {
        return "PositionSnapshot{snapshotTime=" + snapshotTime + ", totalAsset=" + totalAsset + ", availableCash=" + availableCash + ", positionValue=" + positionValue + ", unrealizedPnl=" + unrealizedPnl + ", realizedPnl=" + realizedPnl + ", totalCommission=" + totalCommission + ", tradeCount=" + tradeCount + ", positionCount=" + positionCount + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PositionSnapshot that = (PositionSnapshot) o;
        return Objects.equals(snapshotTime, that.snapshotTime)
                && Objects.equals(totalAsset, that.totalAsset)
                && Objects.equals(availableCash, that.availableCash)
                && Objects.equals(positionValue, that.positionValue)
                && Objects.equals(unrealizedPnl, that.unrealizedPnl)
                && Objects.equals(realizedPnl, that.realizedPnl)
                && Objects.equals(totalCommission, that.totalCommission)
                && Objects.equals(tradeCount, that.tradeCount)
                && Objects.equals(positionCount, that.positionCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotTime, totalAsset, availableCash, positionValue, unrealizedPnl, realizedPnl, totalCommission, tradeCount, positionCount);
    }
}
