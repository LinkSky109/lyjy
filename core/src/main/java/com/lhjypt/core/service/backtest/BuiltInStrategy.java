package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.market.TickData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置示例策略：双均线交叉（仅做多），用于在未传入外部策略脚本时让回测链路闭环。
 *
 * 逻辑：
 * - 用最新价维护短/长周期简单移动均线(SMA)
 * - 金叉(短均线上穿长均线)且空仓：市价买入固定手数
 * - 死叉(短均线下穿长均线)且持多：市价卖出全部平仓
 *
 * 真实策略应通过策略SDK(Python)编写并以信号方式接入，本类仅作引擎自检与演示。
 */
public class BuiltInStrategy {

    private final int shortPeriod;
    private final int longPeriod;
    private final BigDecimal fixedQuantity;

    private final List<BigDecimal> prices = new ArrayList<>();
    private BigDecimal position = BigDecimal.ZERO;
    /** 上一根的短-长均线差，用于判定交叉 */
    private BigDecimal prevDiff = null;

    public BuiltInStrategy() {
        this(5, 20, new BigDecimal("10"));
    }

    public BuiltInStrategy(int shortPeriod, int longPeriod, BigDecimal fixedQuantity) {
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
        this.fixedQuantity = fixedQuantity;
    }

    /**
     * 逐tick处理，产生信号并通过撮合引擎下单。
     */
    public void onTick(TickData tick, MatchingEngine matchingEngine) {
        BigDecimal price = tick.getLastPrice();
        if (price == null) {
            return;
        }
        prices.add(price);
        if (prices.size() < longPeriod) {
            return;
        }

        BigDecimal shortMA = sma(shortPeriod);
        BigDecimal longMA = sma(longPeriod);
        BigDecimal diff = shortMA.subtract(longMA);

        if (prevDiff != null) {
            boolean goldenCross = prevDiff.compareTo(BigDecimal.ZERO) <= 0
                    && diff.compareTo(BigDecimal.ZERO) > 0;
            boolean deathCross = prevDiff.compareTo(BigDecimal.ZERO) >= 0
                    && diff.compareTo(BigDecimal.ZERO) < 0;

            if (goldenCross && position.compareTo(BigDecimal.ZERO) == 0) {
                matchingEngine.submitBuyMarket(tick.getSymbol(), fixedQuantity);
                position = position.add(fixedQuantity);
            } else if (deathCross && position.compareTo(BigDecimal.ZERO) > 0) {
                matchingEngine.submitSellMarket(tick.getSymbol(), position);
                position = BigDecimal.ZERO;
            }
        }
        prevDiff = diff;
    }

    private BigDecimal sma(int period) {
        BigDecimal sum = BigDecimal.ZERO;
        int start = prices.size() - period;
        for (int i = start; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }
        return sum.divide(BigDecimal.valueOf(period), 6, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getPosition() {
        return position;
    }
}
