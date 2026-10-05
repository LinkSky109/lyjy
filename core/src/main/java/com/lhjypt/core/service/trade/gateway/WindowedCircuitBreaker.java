package com.lhjypt.core.service.trade.gateway;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * 网关窗口熔断器。
 *
 * 滑动窗口内达到最小请求数且错误率超过阈值时打开熔断；
 * 熔断窗口结束后进入 HALF_OPEN，只放行一次探测。
 */
public class WindowedCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public record Snapshot(State state, int total, int failures, double failureRate) {
    }

    private record Outcome(long timestamp, boolean failure) {
    }

    private final long windowNanos;
    private final long openNanos;
    private final int minimumCalls;
    private final double failureRateThreshold;
    private final LongSupplier ticker;

    private final Deque<Outcome> outcomes = new ArrayDeque<>();
    private State state = State.CLOSED;
    private long openedAt;
    private boolean probeInFlight;

    public WindowedCircuitBreaker(long windowNanos, long openNanos,
                                  int minimumCalls, double failureRateThreshold,
                                  LongSupplier ticker) {
        this.windowNanos = windowNanos;
        this.openNanos = openNanos;
        this.minimumCalls = minimumCalls;
        this.failureRateThreshold = failureRateThreshold;
        this.ticker = ticker;
    }

    public synchronized boolean allowRequest() {
        advance();
        if (state == State.OPEN) {
            return false;
        }
        if (state == State.HALF_OPEN) {
            if (probeInFlight) {
                return false;
            }
            probeInFlight = true;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN) {
            close();
            return;
        }
        prune();
        outcomes.addLast(new Outcome(ticker.getAsLong(), false));
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            trip();
            return;
        }
        prune();
        outcomes.addLast(new Outcome(ticker.getAsLong(), true));
        evaluate();
    }

    public synchronized Snapshot snapshot() {
        advance();
        prune();
        int total = outcomes.size();
        int failures = (int) outcomes.stream().filter(Outcome::failure).count();
        double rate = total == 0 ? 0.0 : (double) failures / total;
        return new Snapshot(state, total, failures, rate);
    }

    private void advance() {
        if (state == State.OPEN && ticker.getAsLong() - openedAt >= openNanos) {
            state = State.HALF_OPEN;
            probeInFlight = false;
        }
    }

    private void evaluate() {
        int total = outcomes.size();
        if (total < minimumCalls) {
            return;
        }
        long failures = outcomes.stream().filter(Outcome::failure).count();
        if ((double) failures / total > failureRateThreshold) {
            trip();
        }
    }

    private void prune() {
        long cutoff = ticker.getAsLong() - windowNanos;
        while (!outcomes.isEmpty() && outcomes.peekFirst().timestamp() < cutoff) {
            outcomes.removeFirst();
        }
    }

    private void trip() {
        state = State.OPEN;
        openedAt = ticker.getAsLong();
        probeInFlight = false;
    }

    private void close() {
        state = State.CLOSED;
        probeInFlight = false;
        outcomes.clear();
    }
}
