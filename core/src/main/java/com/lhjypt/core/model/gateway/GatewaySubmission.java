package com.lhjypt.core.model.gateway;

/**
 * 网关报单结果。
 */
public record GatewaySubmission(
        String gatewayId,
        String orderRef,
        boolean fallbackUsed,
        String switchedFrom
) {
}
