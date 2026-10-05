package com.lhjypt.core.service.backtest.impl;

import com.lhjypt.core.model.backtest.BacktestConfig;
import com.lhjypt.core.model.backtest.TradeRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 交易成本计算器
 * 计算每笔交易的：手续费、印花税、滑点成本、冲击成本
 *
 * 成本模型：
 * - 手续费 = 成交额 × 手续费率（双边）
 * - 印花税 = 成交额 × 印花税率（仅卖出，股票）
 * - 滑点成本 = 成交量 × 滑点（固定模式）
 * - 冲击成本 = 成交额 × 冲击系数（与成交量/盘口深度相关）
 */
@Component
public class TransactionCostCalculator {

    /** 冲击成本系数（万分之五/单位成交量占比） */
    private static final BigDecimal IMPACT_FACTOR = new BigDecimal("0.0005");

    public void calculateCost(TradeRecord trade, BacktestConfig config) {
        BigDecimal turnover = trade.getTurnover();

        // 1. 手续费（双边）
        BigDecimal commission = turnover.multiply(config.getCommissionRate())
                .setScale(8, RoundingMode.HALF_UP);
        trade.setCommission(commission);

        // 2. 印花税（仅卖出）
        BigDecimal stampDuty = BigDecimal.ZERO;
        if ("SELL".equals(trade.getDirection()) || "COVER".equals(trade.getDirection())) {
            stampDuty = turnover.multiply(config.getStampDutyRate())
                    .setScale(8, RoundingMode.HALF_UP);
        }
        trade.setStampDuty(stampDuty);

        // 3. 滑点成本
        BigDecimal slippageCost = calculateSlippageCost(trade, config);
        trade.setSlippageCost(slippageCost);

        // 4. 冲击成本
        BigDecimal impactCost = BigDecimal.ZERO;
        if (config.isImpactCostEnabled()) {
            impactCost = calculateImpactCost(trade);
        }
        trade.setImpactCost(impactCost);

        // 5. 总成本
        BigDecimal totalCost = commission.add(stampDuty).add(slippageCost).add(impactCost);
        trade.setTotalCost(totalCost);
    }

    private BigDecimal calculateSlippageCost(TradeRecord trade, BacktestConfig config) {
        BigDecimal quantity = trade.getQuantity();
        String mode = config.getSlippageMode();

        switch (mode) {
            case "FIXED":
                // 固定滑点：数量 × 滑点价格
                return quantity.multiply(config.getSlippage())
                        .setScale(8, RoundingMode.HALF_UP);
            case "PERCENT":
                // 百分比滑点：成交额 × 滑点比例
                return trade.getTurnover().multiply(config.getSlippage())
                        .setScale(8, RoundingMode.HALF_UP);
            case "VOLUME":
                // 成交量比例滑点：与成交量平方根成正比
                BigDecimal volumeRatio = quantity.sqrt(new java.math.MathContext(8))
                        .divide(new BigDecimal("1000"), 8, RoundingMode.HALF_UP);
                return trade.getTurnover().multiply(volumeRatio)
                        .setScale(8, RoundingMode.HALF_UP);
            default:
                return quantity.multiply(config.getSlippage())
                        .setScale(8, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal calculateImpactCost(TradeRecord trade) {
        // 简化冲击成本模型：成交额 × 冲击系数 × sqrt(成交量占比)
        // 实际应用中应结合盘口深度计算
        BigDecimal turnover = trade.getTurnover();
        BigDecimal quantity = trade.getQuantity();
        BigDecimal volumeImpact = quantity.sqrt(new java.math.MathContext(8))
                .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        return turnover.multiply(IMPACT_FACTOR).multiply(volumeImpact)
                .setScale(8, RoundingMode.HALF_UP);
    }
}
