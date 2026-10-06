package com.lhjypt.core.service.capacity;

/**
 * 容量指标采集 SPI（本地实测 + 生产采集可替换）。
 */
public interface CapacityMetricsProvider {

    CapacityMetrics sample();
}