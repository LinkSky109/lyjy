package com.lhjypt.core.model.gateway;

/**
 * 路由决策结果。
 */
public record GatewayRouteDecision(
        String primaryGatewayId,
        String gatewayId,
        String fallbackId,
        String ruleId,
        String reason
) {
}
