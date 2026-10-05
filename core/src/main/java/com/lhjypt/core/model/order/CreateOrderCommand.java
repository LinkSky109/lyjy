package com.lhjypt.core.model.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单创建命令
 */
public class CreateOrderCommand {

    /** 客户端幂等键（必填，用于防重） */
    private String idempotentKey;

    /** 策略ID */
    private String strategyId;

    /** 账户ID */
    private String accountId;

    /** 标的代码 */
    private String symbol;

    /** 交易所 */
    private String exchange;

    /** 方向：BUY/SELL */
    private String direction;

    /** 开平：OPEN/CLOSE/CLOSE_TODAY */
    private String offsetFlag;

    /** 订单类型：LIMIT/MARKET/FAK/FOK */
    private String orderType;

    /** 委托价格 */
    private BigDecimal price;

    /** 委托数量 */
    private BigDecimal quantity;

    public String getIdempotentKey() {
        return idempotentKey;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getAccountId() {
        return accountId;
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

    public String getOrderType() {
        return orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setIdempotentKey(String idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

    @Override
    public String toString() {
        return "CreateOrderCommand{idempotentKey=" + idempotentKey + ", strategyId=" + strategyId + ", accountId=" + accountId + ", symbol=" + symbol + ", exchange=" + exchange + ", direction=" + direction + ", offsetFlag=" + offsetFlag + ", orderType=" + orderType + ", price=" + price + ", quantity=" + quantity + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateOrderCommand that = (CreateOrderCommand) o;
        return Objects.equals(idempotentKey, that.idempotentKey)
                && Objects.equals(strategyId, that.strategyId)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(exchange, that.exchange)
                && Objects.equals(direction, that.direction)
                && Objects.equals(offsetFlag, that.offsetFlag)
                && Objects.equals(orderType, that.orderType)
                && Objects.equals(price, that.price)
                && Objects.equals(quantity, that.quantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotentKey, strategyId, accountId, symbol, exchange, direction, offsetFlag, orderType, price, quantity);
    }
}
