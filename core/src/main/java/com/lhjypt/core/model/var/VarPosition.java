package com.lhjypt.core.model.var;

import java.math.BigDecimal;

/**
 * VaR 计算用的持仓腿：带方向的持仓数量。
 *
 * @param symbol   标的
 * @param direction LONG / SHORT
 * @param quantity 持仓数量（非负）
 */
public record VarPosition(String symbol, String direction, BigDecimal quantity) {

    /** 带符号数量：LONG 为正，SHORT 为负。 */
    public double signedQuantity() {
        if (quantity == null) {
            return 0d;
        }
        double sign = "SHORT".equalsIgnoreCase(direction) ? -1d : 1d;
        return sign * quantity.doubleValue();
    }
}
