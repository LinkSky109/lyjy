package com.lhjypt.core.service.risk.impl;

import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.risk.DynamicRiskRule;
import com.lhjypt.core.service.risk.RiskThresholds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 交易频率限制规则
 * - 单位时间内单策略最大下单次数（防止策略失控刷单）
 * - 默认：每秒最多5笔，每分钟最多60笔
 */
@Component
public class OrderFrequencyRule implements DynamicRiskRule {

    private static final int DEFAULT_MAX_PER_SECOND = 5;
    private static final int DEFAULT_MAX_PER_MINUTE = 60;

    @Value("${risk.freq.per-second:5}")
    private int maxPerSecond;

    @Value("${risk.freq.per-minute:60}")
    private int maxPerMinute;

    private boolean enabled = true;

    @Override
    public String getCode() { return "FREQ_LIMIT"; }

    @Override
    public String getName() { return "交易频率限制"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String getDefaultThresholds() {
        return String.format("{\"maxPerSecond\":%d,\"maxPerMinute\":%d}",
                DEFAULT_MAX_PER_SECOND, DEFAULT_MAX_PER_MINUTE);
    }

    @Override
    public void applyThresholds(String thresholdParams) {
        var node = RiskThresholds.parse(thresholdParams);
        int perSecond = RiskThresholds.positiveInt(node, "maxPerSecond", maxPerSecond);
        int perMinute = RiskThresholds.positiveInt(node, "maxPerMinute", maxPerMinute);
        this.maxPerSecond = perSecond;
        this.maxPerMinute = perMinute;
    }

    @Override
    public void check(TradeRequest request, RiskContext context, RiskCheckResult result) {
        if (!enabled) return;

        String strategyId = request.getStrategyId();

        int perSecond = context.getOrderCount(strategyId, 1);
        int perMinute = context.getOrderCount(strategyId, 60);

        if (perSecond >= maxPerSecond) {
            result.addViolation(getCode(), getName(),
                    String.format("策略 %s 每秒下单 %d 笔，超过上限 %d",
                            strategyId, perSecond, maxPerSecond),
                    "REJECT");
        }

        if (perMinute >= maxPerMinute) {
            result.addViolation(getCode(), getName(),
                    String.format("策略 %s 每分钟下单 %d 笔，超过上限 %d",
                            strategyId, perMinute, maxPerMinute),
                    "REJECT");
        }
    }
}
