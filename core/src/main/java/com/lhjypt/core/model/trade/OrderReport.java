package com.lhjypt.core.model.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单回报（CTP OnRtnOrder）
 */
public class OrderReport {

    /** 柜台订单引用 */
    private String orderRef;

    /** 平台订单号 */
    private String clientOrderId;

    /** 来源网关 */
    private String gatewayId;

    /** 网关连接会话 */
    private String gatewaySessionId;

    /** 交易所订单编号 */
    private String exchangeOrderId;

    /** 标的代码 */
    private String symbol;

    /** 方向 */
    private String direction;

    /** 开平标志 */
    private String offsetFlag;

    /** 委托价格 */
    private BigDecimal limitPrice;

    /** 委托数量 */
    private BigDecimal volumeTotalOriginal;

    /** 已成交数量 */
    private BigDecimal volumeTraded;

    /** 订单状态：
     * ALL_TRADED(全部成交)/PART_TRADED(部分成交)/NOT_TRADED(未成交)
     * CANCELED(已撤销)/REJECTED(已拒绝)
     */
    private String orderStatus;

    /** 状态信息 */
    private String statusMsg;

    /** 报单时间 */
    private LocalDateTime insertTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 前置编号 */
    private int frontId;

    /** 会话编号 */
    private int sessionId;

    public String getOrderRef() {
        return orderRef;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public String getGatewaySessionId() {
        return gatewaySessionId;
    }

    public String getExchangeOrderId() {
        return exchangeOrderId;
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

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public BigDecimal getVolumeTotalOriginal() {
        return volumeTotalOriginal;
    }

    public BigDecimal getVolumeTraded() {
        return volumeTraded;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public String getStatusMsg() {
        return statusMsg;
    }

    public LocalDateTime getInsertTime() {
        return insertTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public int getFrontId() {
        return frontId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public void setGatewaySessionId(String gatewaySessionId) {
        this.gatewaySessionId = gatewaySessionId;
    }

    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
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

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    public void setVolumeTotalOriginal(BigDecimal volumeTotalOriginal) {
        this.volumeTotalOriginal = volumeTotalOriginal;
    }

    public void setVolumeTraded(BigDecimal volumeTraded) {
        this.volumeTraded = volumeTraded;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public void setStatusMsg(String statusMsg) {
        this.statusMsg = statusMsg;
    }

    public void setInsertTime(LocalDateTime insertTime) {
        this.insertTime = insertTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public void setFrontId(int frontId) {
        this.frontId = frontId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String toString() {
        return "OrderReport{orderRef=" + orderRef + ", clientOrderId=" + clientOrderId + ", gatewayId=" + gatewayId + ", exchangeOrderId=" + exchangeOrderId + ", symbol=" + symbol + ", direction=" + direction + ", offsetFlag=" + offsetFlag + ", limitPrice=" + limitPrice + ", volumeTotalOriginal=" + volumeTotalOriginal + ", volumeTraded=" + volumeTraded + ", orderStatus=" + orderStatus + ", statusMsg=" + statusMsg + ", insertTime=" + insertTime + ", updateTime=" + updateTime + ", frontId=" + frontId + ", sessionId=" + sessionId + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderReport that = (OrderReport) o;
        return Objects.equals(orderRef, that.orderRef)
                && Objects.equals(clientOrderId, that.clientOrderId)
                && Objects.equals(gatewayId, that.gatewayId)
                && Objects.equals(exchangeOrderId, that.exchangeOrderId)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(direction, that.direction)
                && Objects.equals(offsetFlag, that.offsetFlag)
                && Objects.equals(limitPrice, that.limitPrice)
                && Objects.equals(volumeTotalOriginal, that.volumeTotalOriginal)
                && Objects.equals(volumeTraded, that.volumeTraded)
                && Objects.equals(orderStatus, that.orderStatus)
                && Objects.equals(statusMsg, that.statusMsg)
                && Objects.equals(insertTime, that.insertTime)
                && Objects.equals(updateTime, that.updateTime)
                && Objects.equals(frontId, that.frontId)
                && Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderRef, clientOrderId, gatewayId, exchangeOrderId, symbol, direction, offsetFlag, limitPrice, volumeTotalOriginal, volumeTraded, orderStatus, statusMsg, insertTime, updateTime, frontId, sessionId);
    }
}
