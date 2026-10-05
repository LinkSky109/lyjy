package com.lhjypt.core.model.trade;

/**
 * 统一下单结果。
 */
public record TradeSubmissionResult(
        String orderId,
        String status,
        String brokerOrderRef,
        String mode,
        boolean replay
) {
}
