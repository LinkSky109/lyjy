package com.lhjypt.core.common.exception;

import java.util.List;

/**
 * 交易在进入网关前被拒绝。
 */
public class TradeRejectedException extends RuntimeException {

    private final String code;
    private final List<String> violations;

    public TradeRejectedException(String code, String message) {
        this(code, message, List.of());
    }

    public TradeRejectedException(String code, String message, List<String> violations) {
        super(message);
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public String getCode() {
        return code;
    }

    public List<String> getViolations() {
        return violations;
    }
}
