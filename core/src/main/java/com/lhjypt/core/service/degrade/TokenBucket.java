package com.lhjypt.core.service.degrade;

import java.util.function.LongSupplier;

/**
 * 令牌桶限流器（P1，TASK-P1-004）
 * 本地可运行实现：容量 = 突发上限，refillPerSec = 稳态 TPS。
 */
public class TokenBucket {

    private final long capacity;
    private final double refillPerSec;
    private final LongSupplier nanos;

    private double tokens;
    private long lastNanos;

    public TokenBucket(long capacity, long refillPerSec, LongSupplier nanos) {
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        this.nanos = nanos;
        this.tokens = capacity;
        this.lastNanos = nanos.getAsLong();
    }

    public synchronized boolean tryAcquire() {
        long now = nanos.getAsLong();
        tokens = Math.min(capacity,
                tokens + (now - lastNanos) / 1_000_000_000.0 * refillPerSec);
        lastNanos = now;
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    public synchronized double available() {
        return tokens;
    }

    public long capacity() {
        return capacity;
    }
}
