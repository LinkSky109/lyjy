package com.lhjypt.core.model.risk;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 风控拦截日志
 */
@TableName("risk_intercept_log")
public class RiskInterceptLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被拦截的订单号（如有） */
    private String orderId;

    /** 账户ID */
    private String accountId;

    /** 策略ID */
    private String strategyId;

    /** 标的代码 */
    private String symbol;

    /** 触发的规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 拦截原因 */
    private String reason;

    /** 方向 */
    private String direction;

    /** 委托价格 */
    private BigDecimal price;

    /** 委托数量 */
    private BigDecimal quantity;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getReason() {
        return reason;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "RiskInterceptLog{id=" + id + ", orderId=" + orderId + ", accountId=" + accountId + ", strategyId=" + strategyId + ", symbol=" + symbol + ", ruleCode=" + ruleCode + ", ruleName=" + ruleName + ", reason=" + reason + ", direction=" + direction + ", price=" + price + ", quantity=" + quantity + ", createTime=" + createTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RiskInterceptLog that = (RiskInterceptLog) o;
        return Objects.equals(id, that.id)
                && Objects.equals(orderId, that.orderId)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(strategyId, that.strategyId)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(ruleCode, that.ruleCode)
                && Objects.equals(ruleName, that.ruleName)
                && Objects.equals(reason, that.reason)
                && Objects.equals(direction, that.direction)
                && Objects.equals(price, that.price)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(createTime, that.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, orderId, accountId, strategyId, symbol, ruleCode, ruleName, reason, direction, price, quantity, createTime);
    }
}
