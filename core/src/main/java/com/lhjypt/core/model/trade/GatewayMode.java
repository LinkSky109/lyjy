package com.lhjypt.core.model.trade;

import java.util.Locale;

/**
 * 交易网关运行模式。
 */
public enum GatewayMode {
    MOCK,
    LIVE;

    public static GatewayMode from(String value) {
        if (value == null || value.isBlank()) {
            return MOCK;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知交易运行模式: " + value + "，仅支持 MOCK/LIVE");
        }
    }
}
