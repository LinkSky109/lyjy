package com.lhjypt.core.service.capacity;

/**
 * 容量采样指标。
 */
public record CapacityMetrics(
        double cpuPct,
        double memPct,
        double diskPct,
        int activeStrategies,
        int queueDepth,
        int inboundTps) {
}