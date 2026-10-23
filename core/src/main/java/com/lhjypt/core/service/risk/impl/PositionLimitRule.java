package com.lhjypt.core.service.risk.impl;

import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.risk.DynamicRiskRule;
import com.lhjypt.core.service.risk.RiskThresholds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 持仓限额规则
 * - 单品种最大持仓量限制
 * - 单品种持仓占总资金比例限制
 */
@Component
public class PositionLimitRule implements DynamicRiskRule {

    private static final int DEFAULT_MAX_VOLUME = 100;
    private static final double DEFAULT_MAX_RATIO = 0.3;

    @Value("${risk.position.max-volume:100}")
    private int maxPositionVolume;

    @Value("${risk.position.max-ratio:0.3}")
    private double maxPositionRatio;

    private boolean enabled = true;

    @Override
    public String getCode() { return "POSITION_LIMIT"; }

    @Override
    public String getName() { return "持仓限额"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String getDefaultThresholds() {
        return String.format("{\"maxVolume\":%d,\"maxRatio\":%s}",
                DEFAULT_MAX_VOLUME, BigDecimal.valueOf(DEFAULT_MAX_RATIO).stripTrailingZeros().toPlainString());
    }

    @Override
    public void applyThresholds(String thresholdParams) {
        var node = RiskThresholds.parse(thresholdParams);
        int volume = RiskThresholds.positiveInt(node, "maxVolume", maxPositionVolume);
        double ratio = RiskThresholds.ratio(node, "maxRatio", maxPositionRatio);
        this.maxPositionVolume = volume;
        this.maxPositionRatio = ratio;
    }

    @Override
    public void check(TradeRequest request, RiskContext context, RiskCheckResult result) {
        if (!enabled) return;

        String symbol = request.getSymbol();
        BigDecimal currentPos = context.getCurrentPosition(symbol);
        BigDecimal newPos = "OPEN".equals(request.getOffsetFlag())
                ? currentPos.add(request.getQuantity())
                : currentPos.subtract(request.getQuantity());

        // 1. 最大持仓量检查
        if (newPos.abs().compareTo(BigDecimal.valueOf(maxPositionVolume)) > 0) {
            result.addViolation(getCode(), getName(),
                    String.format("品种 %s 持仓 %s 超过上限 %d",
                            symbol, newPos, maxPositionVolume),
                    "REJECT");
        }

        // 2. 持仓集中度检查（持仓市值/总资金）
        BigDecimal turnover = request.getPrice().multiply(request.getQuantity());
        BigDecimal totalAsset = context.getTotalAsset();
        if (totalAsset.compareTo(BigDecimal.ZERO) > 0) {
            double ratio = turnover.divide(totalAsset, 4, java.math.RoundingMode.HALF_UP)
                    .doubleValue();
            if (ratio > maxPositionRatio) {
                result.addViolation(getCode(), getName(),
                        String.format("品种 %s 持仓占比 %.1f%% 超过上限 %.0f%%",
                                symbol, ratio * 100, maxPositionRatio * 100),
                        "REJECT");
            }
        }
    }
}
