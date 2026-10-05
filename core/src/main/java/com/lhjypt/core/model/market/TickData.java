package com.lhjypt.core.model.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tick行情数据 - Level-2逐笔
 */
public class TickData {

    /** 标的代码 */
    private String symbol;

    /** 最新价 */
    private BigDecimal lastPrice;

    /** 昨收 */
    private BigDecimal preClose;

    /** 开盘价 */
    private BigDecimal open;

    /** 最高价 */
    private BigDecimal high;

    /** 最低价 */
    private BigDecimal low;

    /** 成交量 */
    private long volume;

    /** 成交额 */
    private BigDecimal turnover;

    /** 持仓量（期货） */
    private long openInterest;

    /** 买一价 ~ 买五价 */
    private BigDecimal bidPrice1;
    private BigDecimal bidPrice2;
    private BigDecimal bidPrice3;
    private BigDecimal bidPrice4;
    private BigDecimal bidPrice5;

    /** 买一量 ~ 买五量 */
    private long bidVolume1;
    private long bidVolume2;
    private long bidVolume3;
    private long bidVolume4;
    private long bidVolume5;

    /** 卖一价 ~ 卖五价 */
    private BigDecimal askPrice1;
    private BigDecimal askPrice2;
    private BigDecimal askPrice3;
    private BigDecimal askPrice4;
    private BigDecimal askPrice5;

    /** 卖一量 ~ 卖五量 */
    private long askVolume1;
    private long askVolume2;
    private long askVolume3;
    private long askVolume4;
    private long askVolume5;

    /** 行情时间 */
    private LocalDateTime tickTime;

    /** 接收时间 */
    private LocalDateTime recvTime;

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getPreClose() {
        return preClose;
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

    public long getVolume() {
        return volume;
    }

    public BigDecimal getTurnover() {
        return turnover;
    }

    public long getOpenInterest() {
        return openInterest;
    }

    public BigDecimal getBidPrice1() {
        return bidPrice1;
    }

    public BigDecimal getBidPrice2() {
        return bidPrice2;
    }

    public BigDecimal getBidPrice3() {
        return bidPrice3;
    }

    public BigDecimal getBidPrice4() {
        return bidPrice4;
    }

    public BigDecimal getBidPrice5() {
        return bidPrice5;
    }

    public long getBidVolume1() {
        return bidVolume1;
    }

    public long getBidVolume2() {
        return bidVolume2;
    }

    public long getBidVolume3() {
        return bidVolume3;
    }

    public long getBidVolume4() {
        return bidVolume4;
    }

    public long getBidVolume5() {
        return bidVolume5;
    }

    public BigDecimal getAskPrice1() {
        return askPrice1;
    }

    public BigDecimal getAskPrice2() {
        return askPrice2;
    }

    public BigDecimal getAskPrice3() {
        return askPrice3;
    }

    public BigDecimal getAskPrice4() {
        return askPrice4;
    }

    public BigDecimal getAskPrice5() {
        return askPrice5;
    }

    public long getAskVolume1() {
        return askVolume1;
    }

    public long getAskVolume2() {
        return askVolume2;
    }

    public long getAskVolume3() {
        return askVolume3;
    }

    public long getAskVolume4() {
        return askVolume4;
    }

    public long getAskVolume5() {
        return askVolume5;
    }

    public LocalDateTime getTickTime() {
        return tickTime;
    }

    public LocalDateTime getRecvTime() {
        return recvTime;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public void setPreClose(BigDecimal preClose) {
        this.preClose = preClose;
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

    public void setVolume(long volume) {
        this.volume = volume;
    }

    public void setTurnover(BigDecimal turnover) {
        this.turnover = turnover;
    }

    public void setOpenInterest(long openInterest) {
        this.openInterest = openInterest;
    }

    public void setBidPrice1(BigDecimal bidPrice1) {
        this.bidPrice1 = bidPrice1;
    }

    public void setBidPrice2(BigDecimal bidPrice2) {
        this.bidPrice2 = bidPrice2;
    }

    public void setBidPrice3(BigDecimal bidPrice3) {
        this.bidPrice3 = bidPrice3;
    }

    public void setBidPrice4(BigDecimal bidPrice4) {
        this.bidPrice4 = bidPrice4;
    }

    public void setBidPrice5(BigDecimal bidPrice5) {
        this.bidPrice5 = bidPrice5;
    }

    public void setBidVolume1(long bidVolume1) {
        this.bidVolume1 = bidVolume1;
    }

    public void setBidVolume2(long bidVolume2) {
        this.bidVolume2 = bidVolume2;
    }

    public void setBidVolume3(long bidVolume3) {
        this.bidVolume3 = bidVolume3;
    }

    public void setBidVolume4(long bidVolume4) {
        this.bidVolume4 = bidVolume4;
    }

    public void setBidVolume5(long bidVolume5) {
        this.bidVolume5 = bidVolume5;
    }

    public void setAskPrice1(BigDecimal askPrice1) {
        this.askPrice1 = askPrice1;
    }

    public void setAskPrice2(BigDecimal askPrice2) {
        this.askPrice2 = askPrice2;
    }

    public void setAskPrice3(BigDecimal askPrice3) {
        this.askPrice3 = askPrice3;
    }

    public void setAskPrice4(BigDecimal askPrice4) {
        this.askPrice4 = askPrice4;
    }

    public void setAskPrice5(BigDecimal askPrice5) {
        this.askPrice5 = askPrice5;
    }

    public void setAskVolume1(long askVolume1) {
        this.askVolume1 = askVolume1;
    }

    public void setAskVolume2(long askVolume2) {
        this.askVolume2 = askVolume2;
    }

    public void setAskVolume3(long askVolume3) {
        this.askVolume3 = askVolume3;
    }

    public void setAskVolume4(long askVolume4) {
        this.askVolume4 = askVolume4;
    }

    public void setAskVolume5(long askVolume5) {
        this.askVolume5 = askVolume5;
    }

    public void setTickTime(LocalDateTime tickTime) {
        this.tickTime = tickTime;
    }

    public void setRecvTime(LocalDateTime recvTime) {
        this.recvTime = recvTime;
    }

    @Override
    public String toString() {
        return "TickData{symbol=" + symbol + ", lastPrice=" + lastPrice + ", preClose=" + preClose + ", open=" + open + ", high=" + high + ", low=" + low + ", volume=" + volume + ", turnover=" + turnover + ", openInterest=" + openInterest + ", bidPrice1=" + bidPrice1 + ", bidPrice2=" + bidPrice2 + ", bidPrice3=" + bidPrice3 + ", bidPrice4=" + bidPrice4 + ", bidPrice5=" + bidPrice5 + ", bidVolume1=" + bidVolume1 + ", bidVolume2=" + bidVolume2 + ", bidVolume3=" + bidVolume3 + ", bidVolume4=" + bidVolume4 + ", bidVolume5=" + bidVolume5 + ", askPrice1=" + askPrice1 + ", askPrice2=" + askPrice2 + ", askPrice3=" + askPrice3 + ", askPrice4=" + askPrice4 + ", askPrice5=" + askPrice5 + ", askVolume1=" + askVolume1 + ", askVolume2=" + askVolume2 + ", askVolume3=" + askVolume3 + ", askVolume4=" + askVolume4 + ", askVolume5=" + askVolume5 + ", tickTime=" + tickTime + ", recvTime=" + recvTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TickData that = (TickData) o;
        return Objects.equals(symbol, that.symbol)
                && Objects.equals(lastPrice, that.lastPrice)
                && Objects.equals(preClose, that.preClose)
                && Objects.equals(open, that.open)
                && Objects.equals(high, that.high)
                && Objects.equals(low, that.low)
                && Objects.equals(volume, that.volume)
                && Objects.equals(turnover, that.turnover)
                && Objects.equals(openInterest, that.openInterest)
                && Objects.equals(bidPrice1, that.bidPrice1)
                && Objects.equals(bidPrice2, that.bidPrice2)
                && Objects.equals(bidPrice3, that.bidPrice3)
                && Objects.equals(bidPrice4, that.bidPrice4)
                && Objects.equals(bidPrice5, that.bidPrice5)
                && Objects.equals(bidVolume1, that.bidVolume1)
                && Objects.equals(bidVolume2, that.bidVolume2)
                && Objects.equals(bidVolume3, that.bidVolume3)
                && Objects.equals(bidVolume4, that.bidVolume4)
                && Objects.equals(bidVolume5, that.bidVolume5)
                && Objects.equals(askPrice1, that.askPrice1)
                && Objects.equals(askPrice2, that.askPrice2)
                && Objects.equals(askPrice3, that.askPrice3)
                && Objects.equals(askPrice4, that.askPrice4)
                && Objects.equals(askPrice5, that.askPrice5)
                && Objects.equals(askVolume1, that.askVolume1)
                && Objects.equals(askVolume2, that.askVolume2)
                && Objects.equals(askVolume3, that.askVolume3)
                && Objects.equals(askVolume4, that.askVolume4)
                && Objects.equals(askVolume5, that.askVolume5)
                && Objects.equals(tickTime, that.tickTime)
                && Objects.equals(recvTime, that.recvTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, lastPrice, preClose, open, high, low, volume, turnover, openInterest, bidPrice1, bidPrice2, bidPrice3, bidPrice4, bidPrice5, bidVolume1, bidVolume2, bidVolume3, bidVolume4, bidVolume5, askPrice1, askPrice2, askPrice3, askPrice4, askPrice5, askVolume1, askVolume2, askVolume3, askVolume4, askVolume5, tickTime, recvTime);
    }
}
