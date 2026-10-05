package com.lhjypt.core.model.data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;

/**
 * 数据质量统计 - 实时监控数据质量
 */
public class DataQualityStats {

    private String symbol;

    /** 总接收数 */
    private AtomicLong totalReceived = new AtomicLong(0);

    /** 清洗通过数 */
    private AtomicLong cleanCount = new AtomicLong(0);

    /** 异常数据数 */
    private AtomicLong dirtyCount = new AtomicLong(0);

    /** 缺失数据数 */
    private AtomicLong missingCount = new AtomicLong(0);

    /** 价格跳变数（超过阈值） */
    private AtomicLong priceJumpCount = new AtomicLong(0);

    /** 零值数据数 */
    private AtomicLong zeroValueCount = new AtomicLong(0);

    /** 最后接收时间 */
    private volatile LocalDateTime lastRecvTime;

    /** 最后正常数据时间 */
    private volatile LocalDateTime lastCleanTime;

    public double getDirtyRate() {
        long total = totalReceived.get();
        return total == 0 ? 0 : (double) dirtyCount.get() / total * 100;
    }

    public void reset() {
        totalReceived.set(0);
        cleanCount.set(0);
        dirtyCount.set(0);
        missingCount.set(0);
        priceJumpCount.set(0);
        zeroValueCount.set(0);
    }

    public AtomicLong getTotalReceived() {
        return totalReceived;
    }

    public AtomicLong getCleanCount() {
        return cleanCount;
    }

    public AtomicLong getDirtyCount() {
        return dirtyCount;
    }

    public AtomicLong getMissingCount() {
        return missingCount;
    }

    public AtomicLong getPriceJumpCount() {
        return priceJumpCount;
    }

    public AtomicLong getZeroValueCount() {
        return zeroValueCount;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDateTime getLastRecvTime() {
        return lastRecvTime;
    }

    public LocalDateTime getLastCleanTime() {
        return lastCleanTime;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setLastRecvTime(LocalDateTime lastRecvTime) {
        this.lastRecvTime = lastRecvTime;
    }

    public void setLastCleanTime(LocalDateTime lastCleanTime) {
        this.lastCleanTime = lastCleanTime;
    }

    @Override
    public String toString() {
        return "DataQualityStats{symbol=" + symbol + ", lastRecvTime=" + lastRecvTime + ", lastCleanTime=" + lastCleanTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataQualityStats that = (DataQualityStats) o;
        return Objects.equals(symbol, that.symbol)
                && Objects.equals(lastRecvTime, that.lastRecvTime)
                && Objects.equals(lastCleanTime, that.lastCleanTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, lastRecvTime, lastCleanTime);
    }
}
