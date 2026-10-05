package com.lhjypt.core.model.fund;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 资金对账记录
 * 系统账面资金与柜台返回资金的比对结果
 */
@TableName("fund_reconciliation")
public class FundReconciliation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对账批次号 */
    private String reconNo;

    /** 账户ID */
    private String accountId;

    /** 对账日期 */
    private String reconDate;

    /** 系统账面余额 */
    private BigDecimal systemBalance;

    /** 柜台返回余额 */
    private BigDecimal gatewayBalance;

    /** 系统可用资金 */
    private BigDecimal systemAvailable;

    /** 柜台可用资金 */
    private BigDecimal gatewayAvailable;

    /** 系统保证金 */
    private BigDecimal systemMargin;

    /** 柜台保证金 */
    private BigDecimal gatewayMargin;

    /** 差异金额 */
    private BigDecimal difference;

    /** 对账状态：MATCHED(一致)/MISMATCHED(有差异)/PENDING */
    private String status;

    /** 差异原因 */
    private String differenceReason;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public String getReconNo() {
        return reconNo;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getReconDate() {
        return reconDate;
    }

    public BigDecimal getSystemBalance() {
        return systemBalance;
    }

    public BigDecimal getGatewayBalance() {
        return gatewayBalance;
    }

    public BigDecimal getSystemAvailable() {
        return systemAvailable;
    }

    public BigDecimal getGatewayAvailable() {
        return gatewayAvailable;
    }

    public BigDecimal getSystemMargin() {
        return systemMargin;
    }

    public BigDecimal getGatewayMargin() {
        return gatewayMargin;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public String getStatus() {
        return status;
    }

    public String getDifferenceReason() {
        return differenceReason;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setReconNo(String reconNo) {
        this.reconNo = reconNo;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setReconDate(String reconDate) {
        this.reconDate = reconDate;
    }

    public void setSystemBalance(BigDecimal systemBalance) {
        this.systemBalance = systemBalance;
    }

    public void setGatewayBalance(BigDecimal gatewayBalance) {
        this.gatewayBalance = gatewayBalance;
    }

    public void setSystemAvailable(BigDecimal systemAvailable) {
        this.systemAvailable = systemAvailable;
    }

    public void setGatewayAvailable(BigDecimal gatewayAvailable) {
        this.gatewayAvailable = gatewayAvailable;
    }

    public void setSystemMargin(BigDecimal systemMargin) {
        this.systemMargin = systemMargin;
    }

    public void setGatewayMargin(BigDecimal gatewayMargin) {
        this.gatewayMargin = gatewayMargin;
    }

    public void setDifference(BigDecimal difference) {
        this.difference = difference;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDifferenceReason(String differenceReason) {
        this.differenceReason = differenceReason;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "FundReconciliation{id=" + id + ", reconNo=" + reconNo + ", accountId=" + accountId + ", reconDate=" + reconDate + ", systemBalance=" + systemBalance + ", gatewayBalance=" + gatewayBalance + ", systemAvailable=" + systemAvailable + ", gatewayAvailable=" + gatewayAvailable + ", systemMargin=" + systemMargin + ", gatewayMargin=" + gatewayMargin + ", difference=" + difference + ", status=" + status + ", differenceReason=" + differenceReason + ", createTime=" + createTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FundReconciliation that = (FundReconciliation) o;
        return Objects.equals(id, that.id)
                && Objects.equals(reconNo, that.reconNo)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(reconDate, that.reconDate)
                && Objects.equals(systemBalance, that.systemBalance)
                && Objects.equals(gatewayBalance, that.gatewayBalance)
                && Objects.equals(systemAvailable, that.systemAvailable)
                && Objects.equals(gatewayAvailable, that.gatewayAvailable)
                && Objects.equals(systemMargin, that.systemMargin)
                && Objects.equals(gatewayMargin, that.gatewayMargin)
                && Objects.equals(difference, that.difference)
                && Objects.equals(status, that.status)
                && Objects.equals(differenceReason, that.differenceReason)
                && Objects.equals(createTime, that.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, reconNo, accountId, reconDate, systemBalance, gatewayBalance, systemAvailable, gatewayAvailable, systemMargin, gatewayMargin, difference, status, differenceReason, createTime);
    }
}
