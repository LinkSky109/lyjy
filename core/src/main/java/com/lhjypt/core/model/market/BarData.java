package com.lhjypt.core.model.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * K线数据 - 多周期
 */
public class BarData {

    /** 标的代码 */
    private String symbol;

    /** 周期：1m/5m/15m/1h/1d */
    private String frequency;

    /** 开盘价 */
    private BigDecimal open;

    /** 最高价 */
    private BigDecimal high;

    /** 最低价 */
    private BigDecimal low;

    /** 收盘价 */
    private BigDecimal close;

    /** 成交量 */
    private long volume;

    /** 成交额 */
    private BigDecimal turnover;

    /** 持仓量（期货） */
    private long openInterest;

    /** K线开始时间 */
    private LocalDateTime barTime;

    public String getSymbol() {
        return symbol;
    }

    public String getFrequency() {
        return frequency;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public long getVolume() {
        return volume;
    }

    public BigDecimal getTurnover() {
        return turnover;
    }

    public long getOpenInterest() {
        return openInterest;
    }

    public LocalDateTime getBarTime() {
        return barTime;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }

    public void setTurnover(BigDecimal turnover) {
        this.turnover = turnover;
    }

    public void setOpenInterest(long openInterest) {
        this.openInterest = openInterest;
    }

    public void setBarTime(LocalDateTime barTime) {
        this.barTime = barTime;
    }

    @Override
    public String toString() {
        return "BarData{symbol=" + symbol + ", frequency=" + frequency + ", open=" + open + ", high=" + high + ", low=" + low + ", close=" + close + ", volume=" + volume + ", turnover=" + turnover + ", openInterest=" + openInterest + ", barTime=" + barTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BarData that = (BarData) o;
        return Objects.equals(symbol, that.symbol)
                && Objects.equals(frequency, that.frequency)
                && Objects.equals(open, that.open)
                && Objects.equals(high, that.high)
                && Objects.equals(low, that.low)
                && Objects.equals(close, that.close)
                && Objects.equals(volume, that.volume)
                && Objects.equals(turnover, that.turnover)
                && Objects.equals(openInterest, that.openInterest)
                && Objects.equals(barTime, that.barTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, frequency, open, high, low, close, volume, turnover, openInterest, barTime);
    }
}
