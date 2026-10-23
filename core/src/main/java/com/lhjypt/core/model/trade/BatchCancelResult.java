package com.lhjypt.core.model.trade;

/**
 * 批量撤单逐单结果。
 */
public record BatchCancelResult(
        String orderId,
        boolean success,
        String status,
        String message
) {
}
