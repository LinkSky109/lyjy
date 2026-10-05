package com.lhjypt.core.service.trade.gateway;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowedCircuitBreakerTest {

    @Test
    void opensWhenFailureRateExceedsHalf() {
        AtomicLong ticker = new AtomicLong();
        WindowedCircuitBreaker breaker = breaker(ticker);

        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();

        assertEquals(WindowedCircuitBreaker.State.OPEN, breaker.snapshot().state());
        assertFalse(breaker.allowRequest());
    }

    @Test
    void staysClosedAtExactlyHalfFailureRate() {
        AtomicLong ticker = new AtomicLong();
        WindowedCircuitBreaker breaker = breaker(ticker);

        breaker.recordSuccess();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();

        assertEquals(WindowedCircuitBreaker.State.CLOSED, breaker.snapshot().state());
        assertTrue(breaker.allowRequest());
    }

    @Test
    void halfOpenProbeSuccessClosesAndFailureReopens() {
        AtomicLong ticker = new AtomicLong();
        WindowedCircuitBreaker breaker = breaker(ticker);
        open(breaker);

        ticker.addAndGet(30_000_000_000L);
        assertTrue(breaker.allowRequest());
        assertEquals(WindowedCircuitBreaker.State.HALF_OPEN, breaker.snapshot().state());
        breaker.recordSuccess();
        assertEquals(WindowedCircuitBreaker.State.CLOSED, breaker.snapshot().state());

        open(breaker);
        ticker.addAndGet(30_000_000_000L);
        assertTrue(breaker.allowRequest());
        breaker.recordFailure();
        assertEquals(WindowedCircuitBreaker.State.OPEN, breaker.snapshot().state());
    }

    private static WindowedCircuitBreaker breaker(AtomicLong ticker) {
        return new WindowedCircuitBreaker(
                5_000_000_000L, 30_000_000_000L, 4, 0.5, ticker::get);
    }

    private static void open(WindowedCircuitBreaker breaker) {
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
    }
}
