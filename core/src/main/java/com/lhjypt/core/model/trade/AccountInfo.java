package com.lhjypt.core.model.trade;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 账户资金信息（CTP查询资金）
 */
public class AccountInfo {

    /** 账户ID */
    private String accountId;

    /** 可用资金 */
    private BigDecimal available;

    /** 冻结资金 */
    private BigDecimal frozen;

    /** 总资金 */
    private BigDecimal balance;

    /** 保证金占用 */
    private BigDecimal margin;

    /** 浮动盈亏 */
    private BigDecimal positionPnl;

    /** 平仓盈亏 */
    private BigDecimal closePnl;

    /** 手续费 */
    private BigDecimal commission;

    /** 风险度（保证金/总资金） */
    private BigDecimal riskDegree;

    /** 入金金额 */
    private BigDecimal deposit;

    /** 出金金额 */
    private BigDecimal withdraw;

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getFrozen() {
        return frozen;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getMargin() {
        return margin;
    }

    public BigDecimal getPositionPnl() {
        return positionPnl;
    }

    public BigDecimal getClosePnl() {
        return closePnl;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public BigDecimal getRiskDegree() {
        return riskDegree;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public BigDecimal getWithdraw() {
        return withdraw;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setAvailable(BigDecimal available) {
        this.available = available;
    }

    public void setFrozen(BigDecimal frozen) {
        this.frozen = frozen;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setMargin(BigDecimal margin) {
        this.margin = margin;
    }

    public void setPositionPnl(BigDecimal positionPnl) {
        this.positionPnl = positionPnl;
    }

    public void setClosePnl(BigDecimal closePnl) {
        this.closePnl = closePnl;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public void setRiskDegree(BigDecimal riskDegree) {
        this.riskDegree = riskDegree;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public void setWithdraw(BigDecimal withdraw) {
        this.withdraw = withdraw;
    }

    @Override
    public String toString() {
        return "AccountInfo{accountId=" + accountId + ", available=" + available + ", frozen=" + frozen + ", balance=" + balance + ", margin=" + margin + ", positionPnl=" + positionPnl + ", closePnl=" + closePnl + ", commission=" + commission + ", riskDegree=" + riskDegree + ", deposit=" + deposit + ", withdraw=" + withdraw + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountInfo that = (AccountInfo) o;
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(available, that.available)
                && Objects.equals(frozen, that.frozen)
                && Objects.equals(balance, that.balance)
                && Objects.equals(margin, that.margin)
                && Objects.equals(positionPnl, that.positionPnl)
                && Objects.equals(closePnl, that.closePnl)
                && Objects.equals(commission, that.commission)
                && Objects.equals(riskDegree, that.riskDegree)
                && Objects.equals(deposit, that.deposit)
                && Objects.equals(withdraw, that.withdraw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, available, frozen, balance, margin, positionPnl, closePnl, commission, riskDegree, deposit, withdraw);
    }
}
