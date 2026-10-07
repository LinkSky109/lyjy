package com.lhjypt.core.model.strategy;

import java.util.concurrent.atomic.AtomicLong;

/** 策略自动值守条目（OBS-010，内存管理）。 */
public class WatchdogEntry {

    public static final long DEFAULT_CHECK_MS = 30_000L;

    private final String strategyId;
    private boolean enabled;
    private long checkIntervalMs;
    private final AtomicLong lastAttemptAt = new AtomicLong(0L);
    private String lastStatus = "OFFLINE";
    private String lastError;

    public WatchdogEntry(String strategyId, boolean enabled, Long checkIntervalMs) {
        this.strategyId = strategyId;
        this.enabled = enabled;
        this.checkIntervalMs = checkIntervalMs == null || checkIntervalMs <= 0
                ? DEFAULT_CHECK_MS : checkIntervalMs;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs <= 0 ? DEFAULT_CHECK_MS : checkIntervalMs;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void markAttempt() {
        lastAttemptAt.set(System.currentTimeMillis());
    }

    public boolean withinCooldown() {
        return System.currentTimeMillis() - lastAttemptAt.get() < checkIntervalMs;
    }
}