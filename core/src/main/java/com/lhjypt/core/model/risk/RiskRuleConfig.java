package com.lhjypt.core.model.risk;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 风控规则配置
 */
@TableName("risk_rule_config")
public class RiskRuleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型：POSITION_LIMIT/FUND_CHECK/FREQ_LIMIT/SELF_TRADE/BLACKLIST */
    private String ruleType;

    /** 是否启用 */
    private Boolean enabled;

    /** 阈值参数（JSON格式，如 {"maxPosition":100,"maxRatio":0.3}） */
    private String thresholdParams;

    /** 触发动作：REJECT(拦截)/WARN(告警) */
    private String action;

    /** 适用账户ID，为空则全局生效 */
    private String accountId;

    /** 适用标的，为空则全部标的 */
    private String symbol;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getRuleType() {
        return ruleType;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String getThresholdParams() {
        return thresholdParams;
    }

    public String getAction() {
        return action;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setThresholdParams(String thresholdParams) {
        this.thresholdParams = thresholdParams;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "RiskRuleConfig{id=" + id + ", ruleCode=" + ruleCode + ", ruleName=" + ruleName + ", ruleType=" + ruleType + ", enabled=" + enabled + ", thresholdParams=" + thresholdParams + ", action=" + action + ", accountId=" + accountId + ", symbol=" + symbol + ", createTime=" + createTime + ", updateTime=" + updateTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RiskRuleConfig that = (RiskRuleConfig) o;
        return Objects.equals(id, that.id)
                && Objects.equals(ruleCode, that.ruleCode)
                && Objects.equals(ruleName, that.ruleName)
                && Objects.equals(ruleType, that.ruleType)
                && Objects.equals(enabled, that.enabled)
                && Objects.equals(thresholdParams, that.thresholdParams)
                && Objects.equals(action, that.action)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(createTime, that.createTime)
                && Objects.equals(updateTime, that.updateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ruleCode, ruleName, ruleType, enabled, thresholdParams, action, accountId, symbol, createTime, updateTime);
    }
}
