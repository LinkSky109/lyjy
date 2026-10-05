package com.lhjypt.core.service.degrade;

import java.util.function.LongSupplier;

/**
 * 轻量熔断器（P1，TASK-P1-004）
 * 连续失败达到阈值 → OPEN（拒绝请求）；熔断窗口过后 → HALF_OPEN（放行探测）；
 * 探测成功 → CLOSED；探测失败 → 重新 OPEN。
 * （本地可运行实现，语义对齐 Resilience4j COUNT_BASED + TIME_BASED 的最小子集）
 */
public class SimpleCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openWindowNanos;
    private final LongSupplier ticker;

    private State state = State.CLOSED;
    private int failures = 0;
    private long openedAt = 0;

    public SimpleCircuitBreaker(int failureThreshold, long openWindowNanos, LongSupplier ticker) {
        this.failureThreshold = failureThreshold;
        this.openWindowNanos = openWindowNanos;
        this.ticker = ticker;
    }

    public synchronized boolean allowRequest() {
        advance();
        return state != State.OPEN;
    }

    public synchronized State state() {
        advance();
        return state;
    }

    public synchronized int failures() {
        return failures;
    }

    private void advance() {
        if (state == State.OPEN && ticker.getAsLong() - openedAt >= openWindowNanos) {
            state = State.HALF_OPEN;
        }
    }

    public synchronized void recordSuccess() {
        failures = 0;
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            trip();
            return;
        }
        if (++failures >= failureThreshold) {
            trip();
        }
    }

    private void trip() {
        state = State.OPEN;
        openedAt = ticker.getAsLong();
    }
}
