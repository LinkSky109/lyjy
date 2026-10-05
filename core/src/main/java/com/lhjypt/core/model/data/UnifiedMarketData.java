package com.lhjypt.core.model.data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 统一行情数据模型 - 抹平不同行情源格式差异
 * 所有行情源（CTP/模拟/回放/第三方）最终转换为此模型
 */
public class UnifiedMarketData {

    /** 标的代码（统一格式：交易所.品种，如 SHFE.rb2410） */
    private String symbol;

    /** 交易所 */
    private String exchange;

    /** 品种类型：STOCK/FUTURE/OPTION/BOND */
    private String instrumentType;

    /** 行情源：CTP/MOCK/REPLAY/THIRD_PARTY */
    private String source;

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

    /** 持仓量 */
    private long openInterest;

    /** 五档买卖盘 */
    private Map<Integer, BigDecimal> bidPrices;
    private Map<Integer, Long> bidVolumes;
    private Map<Integer, BigDecimal> askPrices;
    private Map<Integer, Long> askVolumes;

    /** 行情时间（交易所时间） */
    private LocalDateTime marketTime;

    /** 接收时间（本地时间） */
    private LocalDateTime recvTime;

    /** 数据质量标记：CLEAN(正常)/SUSPECT(可疑)/DIRTY(异常) */
    private String qualityFlag;

    /** 数据质量问题描述 */
    private String qualityIssue;

    /** 是否已复权 */
    private boolean adjusted;

    public String getSymbol() {
        return symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public String getSource() {
        return source;
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

    public Map<Integer, BigDecimal> getBidPrices() {
        return bidPrices;
    }

    public Map<Integer, Long> getBidVolumes() {
        return bidVolumes;
    }

    public Map<Integer, BigDecimal> getAskPrices() {
        return askPrices;
    }

    public Map<Integer, Long> getAskVolumes() {
        return askVolumes;
    }

    public LocalDateTime getMarketTime() {
        return marketTime;
    }

    public LocalDateTime getRecvTime() {
        return recvTime;
    }

    public String getQualityFlag() {
        return qualityFlag;
    }

    public String getQualityIssue() {
        return qualityIssue;
    }

    public boolean isAdjusted() {
        return adjusted;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public void setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
    }

    public void setSource(String source) {
        this.source = source;
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

    public void setBidPrices(Map<Integer, BigDecimal> bidPrices) {
        this.bidPrices = bidPrices;
    }

    public void setBidVolumes(Map<Integer, Long> bidVolumes) {
        this.bidVolumes = bidVolumes;
    }

    public void setAskPrices(Map<Integer, BigDecimal> askPrices) {
        this.askPrices = askPrices;
    }

    public void setAskVolumes(Map<Integer, Long> askVolumes) {
        this.askVolumes = askVolumes;
    }

    public void setMarketTime(LocalDateTime marketTime) {
        this.marketTime = marketTime;
    }

    public void setRecvTime(LocalDateTime recvTime) {
        this.recvTime = recvTime;
    }

    public void setQualityFlag(String qualityFlag) {
        this.qualityFlag = qualityFlag;
    }

    public void setQualityIssue(String qualityIssue) {
        this.qualityIssue = qualityIssue;
    }

    public void setAdjusted(boolean adjusted) {
        this.adjusted = adjusted;
    }

    @Override
    public String toString() {
        return "UnifiedMarketData{symbol=" + symbol + ", exchange=" + exchange + ", instrumentType=" + instrumentType + ", source=" + source + ", lastPrice=" + lastPrice + ", preClose=" + preClose + ", open=" + open + ", high=" + high + ", low=" + low + ", volume=" + volume + ", turnover=" + turnover + ", openInterest=" + openInterest + ", bidPrices=" + bidPrices + ", bidVolumes=" + bidVolumes + ", askPrices=" + askPrices + ", askVolumes=" + askVolumes + ", marketTime=" + marketTime + ", recvTime=" + recvTime + ", qualityFlag=" + qualityFlag + ", qualityIssue=" + qualityIssue + ", adjusted=" + adjusted + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnifiedMarketData that = (UnifiedMarketData) o;
        return Objects.equals(symbol, that.symbol)
                && Objects.equals(exchange, that.exchange)
                && Objects.equals(instrumentType, that.instrumentType)
                && Objects.equals(source, that.source)
                && Objects.equals(lastPrice, that.lastPrice)
                && Objects.equals(preClose, that.preClose)
                && Objects.equals(open, that.open)
                && Objects.equals(high, that.high)
                && Objects.equals(low, that.low)
                && Objects.equals(volume, that.volume)
                && Objects.equals(turnover, that.turnover)
                && Objects.equals(openInterest, that.openInterest)
                && Objects.equals(bidPrices, that.bidPrices)
                && Objects.equals(bidVolumes, that.bidVolumes)
                && Objects.equals(askPrices, that.askPrices)
                && Objects.equals(askVolumes, that.askVolumes)
                && Objects.equals(marketTime, that.marketTime)
                && Objects.equals(recvTime, that.recvTime)
                && Objects.equals(qualityFlag, that.qualityFlag)
                && Objects.equals(qualityIssue, that.qualityIssue)
                && Objects.equals(adjusted, that.adjusted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, exchange, instrumentType, source, lastPrice, preClose, open, high, low, volume, turnover, openInterest, bidPrices, bidVolumes, askPrices, askVolumes, marketTime, recvTime, qualityFlag, qualityIssue, adjusted);
    }
}
