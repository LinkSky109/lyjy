package com.lhjypt.core.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 资金账户实体
 */
@TableName("fund_account")
public class FundAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账户ID */
    private String accountId;

    /** 账户名称 */
    private String accountName;

    /** 账户类型：OWN/PRODUCT/MARGIN */
    private String accountType;

    /** 币种 */
    private String currency;

    /** 总资产 */
    private BigDecimal totalAsset;

    /** 可用资金 */
    private BigDecimal availableCash;

    /** 冻结资金 */
    private BigDecimal frozenCash;

    /** 保证金占用 */
    private BigDecimal margin;

    /** 浮动盈亏 */
    private BigDecimal unrealizedPnl;

    /** 已实现盈亏 */
    private BigDecimal realizedPnl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAsset() {
        return totalAsset;
    }

    public BigDecimal getAvailableCash() {
        return availableCash;
    }

    public BigDecimal getFrozenCash() {
        return frozenCash;
    }

    public BigDecimal getMargin() {
        return margin;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
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

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setTotalAsset(BigDecimal totalAsset) {
        this.totalAsset = totalAsset;
    }

    public void setAvailableCash(BigDecimal availableCash) {
        this.availableCash = availableCash;
    }

    public void setFrozenCash(BigDecimal frozenCash) {
        this.frozenCash = frozenCash;
    }

    public void setMargin(BigDecimal margin) {
        this.margin = margin;
    }

    public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
        this.unrealizedPnl = unrealizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "FundAccount{id=" + id + ", accountId=" + accountId + ", accountName=" + accountName + ", accountType=" + accountType + ", currency=" + currency + ", totalAsset=" + totalAsset + ", availableCash=" + availableCash + ", frozenCash=" + frozenCash + ", margin=" + margin + ", unrealizedPnl=" + unrealizedPnl + ", realizedPnl=" + realizedPnl + ", createTime=" + createTime + ", updateTime=" + updateTime + ", deleted=" + deleted + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FundAccount that = (FundAccount) o;
        return Objects.equals(id, that.id)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(accountName, that.accountName)
                && Objects.equals(accountType, that.accountType)
                && Objects.equals(currency, that.currency)
                && Objects.equals(totalAsset, that.totalAsset)
                && Objects.equals(availableCash, that.availableCash)
                && Objects.equals(frozenCash, that.frozenCash)
                && Objects.equals(margin, that.margin)
                && Objects.equals(unrealizedPnl, that.unrealizedPnl)
                && Objects.equals(realizedPnl, that.realizedPnl)
                && Objects.equals(createTime, that.createTime)
                && Objects.equals(updateTime, that.updateTime)
                && Objects.equals(deleted, that.deleted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountId, accountName, accountType, currency, totalAsset, availableCash, frozenCash, margin, unrealizedPnl, realizedPnl, createTime, updateTime, deleted);
    }
}
