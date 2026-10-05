package com.lhjypt.core.model.data;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 复权因子 - 除权除息事件记录
 */
@TableName("adjustment_factor")
public class AdjustmentFactor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标的代码 */
    private String symbol;

    /** 除权除息日期 */
    private LocalDate exDate;

    /** 事件类型：DIVIDEND(分红)/SPLIT(拆股)/RIGHTS_ISSUE(配股)/COMBO(组合) */
    private String eventType;

    /** 每股分红（税前） */
    private BigDecimal dividendPerShare;

    /** 拆分比例（如1拆2为0.5） */
    private BigDecimal splitRatio;

    /** 配股价 */
    private BigDecimal rightsPrice;

    /** 配股比例 */
    private BigDecimal rightsRatio;

    /** 复权因子 = 除权前收盘价 / 除权后参考价 */
    private BigDecimal factor;

    /** 前复权累计因子 */
    private BigDecimal cumulativeFactor;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getExDate() {
        return exDate;
    }

    public String getEventType() {
        return eventType;
    }

    public BigDecimal getDividendPerShare() {
        return dividendPerShare;
    }

    public BigDecimal getSplitRatio() {
        return splitRatio;
    }

    public BigDecimal getRightsPrice() {
        return rightsPrice;
    }

    public BigDecimal getRightsRatio() {
        return rightsRatio;
    }

    public BigDecimal getFactor() {
        return factor;
    }

    public BigDecimal getCumulativeFactor() {
        return cumulativeFactor;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setExDate(LocalDate exDate) {
        this.exDate = exDate;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setDividendPerShare(BigDecimal dividendPerShare) {
        this.dividendPerShare = dividendPerShare;
    }

    public void setSplitRatio(BigDecimal splitRatio) {
        this.splitRatio = splitRatio;
    }

    public void setRightsPrice(BigDecimal rightsPrice) {
        this.rightsPrice = rightsPrice;
    }

    public void setRightsRatio(BigDecimal rightsRatio) {
        this.rightsRatio = rightsRatio;
    }

    public void setFactor(BigDecimal factor) {
        this.factor = factor;
    }

    public void setCumulativeFactor(BigDecimal cumulativeFactor) {
        this.cumulativeFactor = cumulativeFactor;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AdjustmentFactor{id=" + id + ", symbol=" + symbol + ", exDate=" + exDate + ", eventType=" + eventType + ", dividendPerShare=" + dividendPerShare + ", splitRatio=" + splitRatio + ", rightsPrice=" + rightsPrice + ", rightsRatio=" + rightsRatio + ", factor=" + factor + ", cumulativeFactor=" + cumulativeFactor + ", createTime=" + createTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdjustmentFactor that = (AdjustmentFactor) o;
        return Objects.equals(id, that.id)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(exDate, that.exDate)
                && Objects.equals(eventType, that.eventType)
                && Objects.equals(dividendPerShare, that.dividendPerShare)
                && Objects.equals(splitRatio, that.splitRatio)
                && Objects.equals(rightsPrice, that.rightsPrice)
                && Objects.equals(rightsRatio, that.rightsRatio)
                && Objects.equals(factor, that.factor)
                && Objects.equals(cumulativeFactor, that.cumulativeFactor)
                && Objects.equals(createTime, that.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, symbol, exDate, eventType, dividendPerShare, splitRatio, rightsPrice, rightsRatio, factor, cumulativeFactor, createTime);
    }
}
