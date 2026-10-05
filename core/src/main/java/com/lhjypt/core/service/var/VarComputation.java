package com.lhjypt.core.service.var;

/**
 * VaR 计算结果（纯计算产物，不含持久化）。
 */
public record VarComputation(
        String method,
        double confidence,
        int horizonDays,
        double varValue,
        int sampleCount,
        double meanPnl,
        double stdPnl,
        double percentilePnl,
        double minPnl,
        double maxPnl) {
}
