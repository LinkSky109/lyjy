package com.lhjypt.core.service.risk.impl;

import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.risk.DynamicRiskRule;
import com.lhjypt.core.service.risk.RiskThresholds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 资金风控规则
 * - 可用资金充足性检查（开仓所需保证金）
 * - 最低资金留存线
 * - 保证金比例（杠杆）限制
 */
@Component
public class FundRiskRule implements DynamicRiskRule {

    private static final double DEFAULT_MARGIN_RATIO = 0.1;
    private static final double DEFAULT_MIN_CASH_RATIO = 0.1;

    @Value("${risk.fund.margin-ratio:0.1}")
    private double marginRatio;

    @Value("${risk.fund.min-cash-ratio:0.1}")
    private double minCashRatio;

    private boolean enabled = true;

    @Override
    public String getCode() { return "FUND_CHECK"; }

    @Override
    public String getName() { return "资金风控"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String getDefaultThresholds() {
        return String.format("{\"marginRatio\":%s,\"minCashRatio\":%s}",
                BigDecimal.valueOf(DEFAULT_MARGIN_RATIO).stripTrailingZeros().toPlainString(),
                BigDecimal.valueOf(DEFAULT_MIN_CASH_RATIO).stripTrailingZeros().toPlainString());
    }

    @Override
    public void applyThresholds(String thresholdParams) {
        var node = RiskThresholds.parse(thresholdParams);
        double margin = RiskThresholds.ratio(node, "marginRatio", marginRatio);
        double minCash = RiskThresholds.ratio(node, "minCashRatio", minCashRatio);
        this.marginRatio = margin;
        this.minCashRatio = minCash;
    }

    @Override
    public void check(TradeRequest request, RiskContext context, RiskCheckResult result) {
        if (!enabled) return;

        BigDecimal totalAsset = context.getTotalAsset();
        BigDecimal available = context.getAvailableCash();

        if ("OPEN".equals(request.getOffsetFlag())) {
            BigDecimal turnover = request.getPrice().multiply(request.getQuantity());
            BigDecimal requiredMargin = turnover.multiply(BigDecimal.valueOf(marginRatio))
                    .setScale(2, RoundingMode.HALF_UP);

            // 1. 可用资金是否足够支付保证金
            if (available.compareTo(requiredMargin) < 0) {
                result.addViolation(getCode(), getName(),
                        String.format("可用资金 %s 不足，开仓需保证金 %s",
                                available, requiredMargin),
                        "REJECT");
            }

            // 2. 最低资金留存检查
            BigDecimal minCash = totalAsset.multiply(BigDecimal.valueOf(minCashRatio));
            if (available.subtract(requiredMargin).compareTo(minCash) < 0) {
                result.addViolation(getCode(), getName(),
                        String.format("开仓后可用资金将低于最低留存线 %s", minCash),
                        "REJECT");
            }
        }
    }
}
