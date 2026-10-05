package com.lhjypt.core.model.trade;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 下单请求
 */
public class TradeRequest {

    /** 标的代码 */
    private String symbol;

    /** 交易所代码 */
    private String exchange;

    /** 方向：BUY(买)/SELL(卖) */
    private String direction;

    /** 开平标志：OPEN(开仓)/CLOSE(平仓)/CLOSE_TODAY(平今)/CLOSE_YESTERDAY(平昨) */
    private String offsetFlag;

    /** 订单类型：LIMIT(限价)/MARKET(市价)/FAK(部成部撤)/FOK(全成或撤) */
    private String orderType;

    /** 委托价格 */
    private BigDecimal price;

    /** 委托数量 */
    private BigDecimal quantity;

    /** 策略ID */
    private String strategyId;

    /** 账户ID */
    private String accountId;

    /** 最小成交量 */
    private BigDecimal minVolume;

    /** 备注 */
    private String remark;

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

    public String getOrderType() {
        return orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getMinVolume() {
        return minVolume;
    }

    public String getRemark() {
        return remark;
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

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setMinVolume(BigDecimal minVolume) {
        this.minVolume = minVolume;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "TradeRequest{symbol=" + symbol + ", exchange=" + exchange + ", direction=" + direction + ", offsetFlag=" + offsetFlag + ", orderType=" + orderType + ", price=" + price + ", quantity=" + quantity + ", strategyId=" + strategyId + ", accountId=" + accountId + ", minVolume=" + minVolume + ", remark=" + remark + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeRequest that = (TradeRequest) o;
        return Objects.equals(symbol, that.symbol)
                && Objects.equals(exchange, that.exchange)
                && Objects.equals(direction, that.direction)
                && Objects.equals(offsetFlag, that.offsetFlag)
                && Objects.equals(orderType, that.orderType)
                && Objects.equals(price, that.price)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(strategyId, that.strategyId)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(minVolume, that.minVolume)
                && Objects.equals(remark, that.remark);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, exchange, direction, offsetFlag, orderType, price, quantity, strategyId, accountId, minVolume, remark);
    }
}
