package com.lhjypt.core.model.backtest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 交易记录 - 回测中每一笔成交
 */
public class TradeRecord {

    /** 交易ID */
    private String tradeId;

    /** 回测ID */
    private String backtestId;

    /** 标的代码 */
    private String symbol;

    /** 方向：BUY/SELL/SHORT/COVER */
    private String direction;

    /** 成交价格 */
    private BigDecimal price;

    /** 成交数量 */
    private BigDecimal quantity;

    /** 成交金额 */
    private BigDecimal turnover;

    /** 手续费 */
    private BigDecimal commission;

    /** 印花税 */
    private BigDecimal stampDuty;

    /** 滑点成本 */
    private BigDecimal slippageCost;

    /** 冲击成本 */
    private BigDecimal impactCost;

    /** 总成本 */
    private BigDecimal totalCost;

    /** 撮合理由：LIMIT_PRICE(限价成交)/MARKET(市价)/STOP(止损) */
    private String matchReason;

    /** 成交时间 */
    private LocalDateTime tradeTime;

    /** 订单ID（关联） */
    private String orderId;

    public String getTradeId() {
        return tradeId;
    }

    public String getBacktestId() {
        return backtestId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getTurnover() {
        return turnover;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public BigDecimal getStampDuty() {
        return stampDuty;
    }

    public BigDecimal getSlippageCost() {
        return slippageCost;
    }

    public BigDecimal getImpactCost() {
        return impactCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public LocalDateTime getTradeTime() {
        return tradeTime;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public void setBacktestId(String backtestId) {
        this.backtestId = backtestId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setTurnover(BigDecimal turnover) {
        this.turnover = turnover;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public void setStampDuty(BigDecimal stampDuty) {
        this.stampDuty = stampDuty;
    }

    public void setSlippageCost(BigDecimal slippageCost) {
        this.slippageCost = slippageCost;
    }

    public void setImpactCost(BigDecimal impactCost) {
        this.impactCost = impactCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }

    public void setTradeTime(LocalDateTime tradeTime) {
        this.tradeTime = tradeTime;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String toString() {
        return "TradeRecord{tradeId=" + tradeId + ", backtestId=" + backtestId + ", symbol=" + symbol + ", direction=" + direction + ", price=" + price + ", quantity=" + quantity + ", turnover=" + turnover + ", commission=" + commission + ", stampDuty=" + stampDuty + ", slippageCost=" + slippageCost + ", impactCost=" + impactCost + ", totalCost=" + totalCost + ", matchReason=" + matchReason + ", tradeTime=" + tradeTime + ", orderId=" + orderId + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeRecord that = (TradeRecord) o;
        return Objects.equals(tradeId, that.tradeId)
                && Objects.equals(backtestId, that.backtestId)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(direction, that.direction)
                && Objects.equals(price, that.price)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(turnover, that.turnover)
                && Objects.equals(commission, that.commission)
                && Objects.equals(stampDuty, that.stampDuty)
                && Objects.equals(slippageCost, that.slippageCost)
                && Objects.equals(impactCost, that.impactCost)
                && Objects.equals(totalCost, that.totalCost)
                && Objects.equals(matchReason, that.matchReason)
                && Objects.equals(tradeTime, that.tradeTime)
                && Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId, backtestId, symbol, direction, price, quantity, turnover, commission, stampDuty, slippageCost, impactCost, totalCost, matchReason, tradeTime, orderId);
    }
}
