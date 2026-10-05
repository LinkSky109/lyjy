package com.lhjypt.core.model.trade;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 持仓明细（CTP查询持仓）
 */
public class PositionDetail {

    /** 标的代码 */
    private String symbol;

    /** 交易所 */
    private String exchange;

    /** 方向：LONG(多)/SHORT(空) */
    private String direction;

    /** 总持仓 */
    private BigDecimal totalVolume;

    /** 今仓 */
    private BigDecimal todayVolume;

    /** 昨仓 */
    private BigDecimal yesterdayVolume;

    /** 可用持仓（可平仓） */
    private BigDecimal availableVolume;

    /** 持仓成本 */
    private BigDecimal avgCost;

    /** 开仓成本 */
    private BigDecimal openCost;

    /** 持仓盈亏 */
    private BigDecimal positionPnl;

    /** 平仓盈亏 */
    private BigDecimal closePnl;

    /** 占用保证金 */
    private BigDecimal margin;

    /** 手续费 */
    private BigDecimal commission;

    public String getSymbol() {
        return symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getTotalVolume() {
        return totalVolume;
    }

    public BigDecimal getTodayVolume() {
        return todayVolume;
    }

    public BigDecimal getYesterdayVolume() {
        return yesterdayVolume;
    }

    public BigDecimal getAvailableVolume() {
        return availableVolume;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public BigDecimal getOpenCost() {
        return openCost;
    }

    public BigDecimal getPositionPnl() {
        return positionPnl;
    }

    public BigDecimal getClosePnl() {
        return closePnl;
    }

    public BigDecimal getMargin() {
        return margin;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setTotalVolume(BigDecimal totalVolume) {
        this.totalVolume = totalVolume;
    }

    public void setTodayVolume(BigDecimal todayVolume) {
        this.todayVolume = todayVolume;
    }

    public void setYesterdayVolume(BigDecimal yesterdayVolume) {
        this.yesterdayVolume = yesterdayVolume;
    }

    public void setAvailableVolume(BigDecimal availableVolume) {
        this.availableVolume = availableVolume;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }

    public void setOpenCost(BigDecimal openCost) {
        this.openCost = openCost;
    }

    public void setPositionPnl(BigDecimal positionPnl) {
        this.positionPnl = positionPnl;
    }

    public void setClosePnl(BigDecimal closePnl) {
        this.closePnl = closePnl;
    }

    public void setMargin(BigDecimal margin) {
        this.margin = margin;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    @Override
    public String toString() {
        return "PositionDetail{symbol=" + symbol + ", exchange=" + exchange + ", direction=" + direction + ", totalVolume=" + totalVolume + ", todayVolume=" + todayVolume + ", yesterdayVolume=" + yesterdayVolume + ", availableVolume=" + availableVolume + ", avgCost=" + avgCost + ", openCost=" + openCost + ", positionPnl=" + positionPnl + ", closePnl=" + closePnl + ", margin=" + margin + ", commission=" + commission + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PositionDetail that = (PositionDetail) o;
        return Objects.equals(symbol, that.symbol)
                && Objects.equals(exchange, that.exchange)
                && Objects.equals(direction, that.direction)
                && Objects.equals(totalVolume, that.totalVolume)
                && Objects.equals(todayVolume, that.todayVolume)
                && Objects.equals(yesterdayVolume, that.yesterdayVolume)
                && Objects.equals(availableVolume, that.availableVolume)
                && Objects.equals(avgCost, that.avgCost)
                && Objects.equals(openCost, that.openCost)
                && Objects.equals(positionPnl, that.positionPnl)
                && Objects.equals(closePnl, that.closePnl)
                && Objects.equals(margin, that.margin)
                && Objects.equals(commission, that.commission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, exchange, direction, totalVolume, todayVolume, yesterdayVolume, availableVolume, avgCost, openCost, positionPnl, closePnl, margin, commission);
    }
}
