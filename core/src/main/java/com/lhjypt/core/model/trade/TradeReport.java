package com.lhjypt.core.model.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 成交回报（CTP OnRtnTrade）
 */
public class TradeReport {

    /** 成交编号 */
    private String tradeId;

    /** 订单引用 */
    private String orderRef;

    /** 平台订单号 */
    private String clientOrderId;

    /** 交易所订单编号 */
    private String exchangeOrderId;

    /** 标的代码 */
    private String symbol;

    /** 交易所 */
    private String exchange;

    /** 方向 */
    private String direction;

    /** 开平标志 */
    private String offsetFlag;

    /** 成交价格 */
    private BigDecimal price;

    /** 成交数量 */
    private BigDecimal volume;

    /** 成交金额 */
    private BigDecimal turnover;

    /** 成交时间 */
    private LocalDateTime tradeTime;

    /** 交易日 */
    private String tradingDay;

    public String getTradeId() {
        return tradeId;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public String getDirection() {
        return direction;
    }

    public String getOffsetFlag() {
        return offsetFlag;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public BigDecimal getTurnover() {
        return turnover;
    }

    public LocalDateTime getTradeTime() {
        return tradeTime;
    }

    public String getTradingDay() {
        return tradingDay;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public void setOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setOffsetFlag(String offsetFlag) {
        this.offsetFlag = offsetFlag;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public void setTurnover(BigDecimal turnover) {
        this.turnover = turnover;
    }

    public void setTradeTime(LocalDateTime tradeTime) {
        this.tradeTime = tradeTime;
    }

    public void setTradingDay(String tradingDay) {
        this.tradingDay = tradingDay;
    }

    @Override
    public String toString() {
        return "TradeReport{tradeId=" + tradeId + ", orderRef=" + orderRef + ", clientOrderId=" + clientOrderId + ", exchangeOrderId=" + exchangeOrderId + ", symbol=" + symbol + ", exchange=" + exchange + ", direction=" + direction + ", offsetFlag=" + offsetFlag + ", price=" + price + ", volume=" + volume + ", turnover=" + turnover + ", tradeTime=" + tradeTime + ", tradingDay=" + tradingDay + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeReport that = (TradeReport) o;
        return Objects.equals(tradeId, that.tradeId)
                && Objects.equals(orderRef, that.orderRef)
                && Objects.equals(clientOrderId, that.clientOrderId)
                && Objects.equals(exchangeOrderId, that.exchangeOrderId)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(exchange, that.exchange)
                && Objects.equals(direction, that.direction)
                && Objects.equals(offsetFlag, that.offsetFlag)
                && Objects.equals(price, that.price)
                && Objects.equals(volume, that.volume)
                && Objects.equals(turnover, that.turnover)
                && Objects.equals(tradeTime, that.tradeTime)
                && Objects.equals(tradingDay, that.tradingDay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId, orderRef, clientOrderId, exchangeOrderId, symbol, exchange, direction, offsetFlag, price, volume, turnover, tradeTime, tradingDay);
    }
}
