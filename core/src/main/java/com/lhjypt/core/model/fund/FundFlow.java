package com.lhjypt.core.model.fund;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 资金流水
 * 记录所有资金变动，形成不可篡改的流水账
 */
@TableName("fund_flow")
public class FundFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流水号 */
    private String flowNo;

    /** 账户ID */
    private String accountId;

    /** 业务类型：DEPOSIT/WITHDRAW/MARGIN_FREEZE/MARGIN_RELEASE/
     * COMMISSION/PROFIT_LOSS/TRANSFER */
    private String bizType;

    /** 关联业务单号（订单号/成交号/出入金单号） */
    private String bizNo;

    /** 变动金额（正为增加，负为减少） */
    private BigDecimal changeAmount;

    /** 变动前余额 */
    private BigDecimal balanceBefore;

    /** 变动后余额 */
    private BigDecimal balanceAfter;

    /** 标的代码 */
    private String symbol;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public String getFlowNo() {
        return flowNo;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getBizType() {
        return bizType;
    }

    public String getBizNo() {
        return bizNo;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getRemark() {
        return remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setFlowNo(String flowNo) {
        this.flowNo = flowNo;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public void setChangeAmount(BigDecimal changeAmount) {
        this.changeAmount = changeAmount;
    }

    public void setBalanceBefore(BigDecimal balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "FundFlow{id=" + id + ", flowNo=" + flowNo + ", accountId=" + accountId + ", bizType=" + bizType + ", bizNo=" + bizNo + ", changeAmount=" + changeAmount + ", balanceBefore=" + balanceBefore + ", balanceAfter=" + balanceAfter + ", symbol=" + symbol + ", remark=" + remark + ", createTime=" + createTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FundFlow that = (FundFlow) o;
        return Objects.equals(id, that.id)
                && Objects.equals(flowNo, that.flowNo)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(bizType, that.bizType)
                && Objects.equals(bizNo, that.bizNo)
                && Objects.equals(changeAmount, that.changeAmount)
                && Objects.equals(balanceBefore, that.balanceBefore)
                && Objects.equals(balanceAfter, that.balanceAfter)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(remark, that.remark)
                && Objects.equals(createTime, that.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, flowNo, accountId, bizType, bizNo, changeAmount, balanceBefore, balanceAfter, symbol, remark, createTime);
    }
}
