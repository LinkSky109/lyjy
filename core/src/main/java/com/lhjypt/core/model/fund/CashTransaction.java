package com.lhjypt.core.model.fund;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 出入金申请
 */
@TableName("cash_transaction")
public class CashTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流水号 */
    private String transactionNo;

    /** 账户ID */
    private String accountId;

    /** 类型：DEPOSIT(入金)/WITHDRAW(出金) */
    private String type;

    /** 金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency = "CNY";

    /** 状态：PENDING(待审核)/APPROVED(已批准)/REJECTED(已拒绝)/COMPLETED(已完成) */
    private String status;

    /** 银行账户 */
    private String bankAccount;

    /** 申请人 */
    private String applicant;

    /** 审核人 */
    private String approver;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime approveTime;
    private LocalDateTime completeTime;

    public Long getId() {
        return id;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public String getApplicant() {
        return applicant;
    }

    public String getApprover() {
        return approver;
    }

    public String getRemark() {
        return remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getApproveTime() {
        return approveTime;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public void setApplicant(String applicant) {
        this.applicant = applicant;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setApproveTime(LocalDateTime approveTime) {
        this.approveTime = approveTime;
    }

    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
    }

    @Override
    public String toString() {
        return "CashTransaction{id=" + id + ", transactionNo=" + transactionNo + ", accountId=" + accountId + ", type=" + type + ", amount=" + amount + ", status=" + status + ", bankAccount=" + bankAccount + ", applicant=" + applicant + ", approver=" + approver + ", remark=" + remark + ", createTime=" + createTime + ", approveTime=" + approveTime + ", completeTime=" + completeTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CashTransaction that = (CashTransaction) o;
        return Objects.equals(id, that.id)
                && Objects.equals(transactionNo, that.transactionNo)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(type, that.type)
                && Objects.equals(amount, that.amount)
                && Objects.equals(status, that.status)
                && Objects.equals(bankAccount, that.bankAccount)
                && Objects.equals(applicant, that.applicant)
                && Objects.equals(approver, that.approver)
                && Objects.equals(remark, that.remark)
                && Objects.equals(createTime, that.createTime)
                && Objects.equals(approveTime, that.approveTime)
                && Objects.equals(completeTime, that.completeTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transactionNo, accountId, type, amount, status, bankAccount, applicant, approver, remark, createTime, approveTime, completeTime);
    }
}
