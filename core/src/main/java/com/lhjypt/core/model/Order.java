package com.lhjypt.core.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单实体 - OMS订单管理
 */
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（全局唯一） */
    private String orderId;

    /** 策略ID */
    private String strategyId;

    /** 账户ID */
    private String accountId;

    /** 标的代码 */
    private String symbol;

    /** 买卖方向：BUY/SELL */
    private String direction;

    /** 开平标志：OPEN/CLOSE/CLOSE_TODAY/CLOSE_YESTERDAY */
    private String offsetFlag;

    /** 订单类型：LIMIT/MARKET/STOP */
    private String orderType;

    /** 委托价格 */
    private BigDecimal price;

    /** 委托数量 */
    private BigDecimal quantity;

    /** 成交数量 */
    private BigDecimal filledQuantity;

    /** 成交均价 */
    private BigDecimal avgFillPrice;

    /** 订单状态：PENDING/SUBMITTED/PARTIAL_FILLED/FILLED/CANCELED/REJECTED */
    private String status;

    /** 柜台回报编号 */
    private String exchangeOrderId;

    /** 实际使用的网关 */
    private String gatewayId;

    /** 柜台订单引用 */
    private String brokerOrderRef;

    /** 错误码 */
    private String errorCode;

    /** 错误信息（拒单时） */
    private String errorMessage;

    /** 提交柜台时间 */
    private LocalDateTime submittedAt;

    /** 全部成交时间 */
    private LocalDateTime filledAt;

    /** 撤销时间 */
    private LocalDateTime canceledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
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

    public BigDecimal getFilledQuantity() {
        return filledQuantity;
    }

    public BigDecimal getAvgFillPrice() {
        return avgFillPrice;
    }

    public String getStatus() {
        return status;
    }

    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public String getBrokerOrderRef() {
        return brokerOrderRef;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getFilledAt() {
        return filledAt;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public void setFilledQuantity(BigDecimal filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    public void setAvgFillPrice(BigDecimal avgFillPrice) {
        this.avgFillPrice = avgFillPrice;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public void setBrokerOrderRef(String brokerOrderRef) {
        this.brokerOrderRef = brokerOrderRef;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public void setFilledAt(LocalDateTime filledAt) {
        this.filledAt = filledAt;
    }

    public void setCanceledAt(LocalDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", orderId=" + orderId + ", strategyId=" + strategyId + ", accountId=" + accountId + ", symbol=" + symbol + ", direction=" + direction + ", offsetFlag=" + offsetFlag + ", orderType=" + orderType + ", price=" + price + ", quantity=" + quantity + ", filledQuantity=" + filledQuantity + ", avgFillPrice=" + avgFillPrice + ", status=" + status + ", brokerOrderRef=" + brokerOrderRef + ", exchangeOrderId=" + exchangeOrderId + ", gatewayId=" + gatewayId + ", errorCode=" + errorCode + ", errorMessage=" + errorMessage + ", submittedAt=" + submittedAt + ", filledAt=" + filledAt + ", canceledAt=" + canceledAt + ", createTime=" + createTime + ", updateTime=" + updateTime + ", deleted=" + deleted + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order that = (Order) o;
        return Objects.equals(id, that.id)
                && Objects.equals(orderId, that.orderId)
                && Objects.equals(strategyId, that.strategyId)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(direction, that.direction)
                && Objects.equals(offsetFlag, that.offsetFlag)
                && Objects.equals(orderType, that.orderType)
                && Objects.equals(price, that.price)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(filledQuantity, that.filledQuantity)
                && Objects.equals(avgFillPrice, that.avgFillPrice)
                && Objects.equals(status, that.status)
                && Objects.equals(brokerOrderRef, that.brokerOrderRef)
                && Objects.equals(exchangeOrderId, that.exchangeOrderId)
                && Objects.equals(gatewayId, that.gatewayId)
                && Objects.equals(errorCode, that.errorCode)
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(submittedAt, that.submittedAt)
                && Objects.equals(filledAt, that.filledAt)
                && Objects.equals(canceledAt, that.canceledAt)
                && Objects.equals(createTime, that.createTime)
                && Objects.equals(updateTime, that.updateTime)
                && Objects.equals(deleted, that.deleted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, orderId, strategyId, accountId, symbol, direction, offsetFlag, orderType, price, quantity, filledQuantity, avgFillPrice, status, brokerOrderRef, exchangeOrderId, gatewayId, errorCode, errorMessage, submittedAt, filledAt, canceledAt, createTime, updateTime, deleted);
    }
}
