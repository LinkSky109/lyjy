package com.lhjypt.core.service.market.source;

/**
 * 统一行情 K 线（P3-006 多源）。时间戳为秒。
 */
public record MarketCandle(long timestamp, String open, String high, String low,
                           String close, String volume, String amount) {
}