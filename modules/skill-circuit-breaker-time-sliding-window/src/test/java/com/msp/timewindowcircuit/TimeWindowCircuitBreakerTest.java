package com.msp.timewindowcircuit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimeWindowCircuitBreaker Tests")
class TimeWindowCircuitBreakerTest {

    private static final class MutableClock extends Clock {
        private final AtomicLong millis;
        private final ZoneId zone;

        MutableClock(long initialMillis) {
            this.millis = new AtomicLong(initialMillis);
            this.zone = ZoneId.of("UTC");
        }

        void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    @Test
    @DisplayName("Normal CLOSED state permits requests and tracks failure rate accurately")
    void testClosedStateOperation() {
        MutableClock clock = new MutableClock(10000);
        TimeWindowCircuitBreaker breaker = new TimeWindowCircuitBreaker(
                Duration.ofSeconds(10), 50, Duration.ofSeconds(5), 5, 10, clock
        );

        assertEquals(CircuitState.CLOSED, breaker.getState());
        assertTrue(breaker.tryAcquirePermission());

        breaker.recordSuccess();
        breaker.recordSuccess();
        breaker.recordFailure();

        assertEquals(CircuitState.CLOSED, breaker.getState());
        assertEquals(33.33, breaker.getFailureRate(), 0.1);
    }

    @Test
    @DisplayName("Breaker trips to OPEN when failure rate threshold is breached")
    void testTripToOpen() {
        MutableClock clock = new MutableClock(10000);
        TimeWindowCircuitBreaker breaker = new TimeWindowCircuitBreaker(
                Duration.ofSeconds(10), 50, Duration.ofSeconds(5), 4, 10, clock
        );

        // 2 successes, 2 failures -> 50% failure rate with 4 calls
        breaker.recordSuccess();
        breaker.recordSuccess();
        breaker.recordFailure();
        assertEquals(CircuitState.CLOSED, breaker.getState());

        breaker.recordFailure(); // Reaches 50% failure threshold
        assertEquals(CircuitState.OPEN, breaker.getState());
        assertFalse(breaker.tryAcquirePermission(), "Calls must be rejected in OPEN state");
    }

    @Test
    @DisplayName("OPEN transitions to HALF_OPEN after openDuration, recovery on success")
    void testOpenToHalfOpenAndRecovery() {
        MutableClock clock = new MutableClock(10000);
        TimeWindowCircuitBreaker breaker = new TimeWindowCircuitBreaker(
                Duration.ofSeconds(10), 50, Duration.ofSeconds(5), 2, 10, clock
        );

        // Trip the breaker
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(CircuitState.OPEN, breaker.getState());

        // Fast forward 4 seconds (still OPEN)
        clock.advance(Duration.ofSeconds(4));
        assertEquals(CircuitState.OPEN, breaker.getState());
        assertFalse(breaker.tryAcquirePermission());

        // Fast forward 2 more seconds (total 6s > openDuration 5s)
        clock.advance(Duration.ofSeconds(2));
        assertEquals(CircuitState.HALF_OPEN, breaker.getState());

        // Permit trial request
        assertTrue(breaker.tryAcquirePermission());
        // Trial succeeds -> transitions back to CLOSED
        breaker.recordSuccess();
        assertEquals(CircuitState.CLOSED, breaker.getState());
        assertTrue(breaker.tryAcquirePermission());
    }

    @Test
    @DisplayName("HALF_OPEN trips back to OPEN immediately if trial request fails")
    void testHalfOpenTripBackOnFailure() {
        MutableClock clock = new MutableClock(10000);
        TimeWindowCircuitBreaker breaker = new TimeWindowCircuitBreaker(
                Duration.ofSeconds(10), 50, Duration.ofSeconds(5), 2, 10, clock
        );

        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(CircuitState.OPEN, breaker.getState());

        clock.advance(Duration.ofSeconds(6));
        assertEquals(CircuitState.HALF_OPEN, breaker.getState());

        assertTrue(breaker.tryAcquirePermission());
        // Trial request fails!
        breaker.recordFailure();
        assertEquals(CircuitState.OPEN, breaker.getState());
        assertFalse(breaker.tryAcquirePermission());
    }

    @Test
    @DisplayName("Sliding time window expires old buckets")
    void testSlidingWindowExpiration() {
        MutableClock clock = new MutableClock(10000);
        TimeWindowCircuitBreaker breaker = new TimeWindowCircuitBreaker(
                Duration.ofSeconds(5), 80, Duration.ofSeconds(5), 3, 5, clock
        );

        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(100.0, breaker.getFailureRate(), 0.01);

        // Advance beyond window (6 seconds)
        clock.advance(Duration.ofSeconds(6));
        // Old failures have aged out
        assertEquals(0.0, breaker.getFailureRate(), 0.01);
    }

    @Test
    @DisplayName("Reset resets state to CLOSED and clears counters")
    void testReset() {
        TimeWindowCircuitBreaker breaker = new TimeWindowCircuitBreaker(
                Duration.ofSeconds(10), 50, Duration.ofSeconds(5)
        );
        for (int i = 0; i < 10; i++) {
            breaker.recordFailure();
        }
        assertEquals(CircuitState.OPEN, breaker.getState());

        breaker.reset();
        assertEquals(CircuitState.CLOSED, breaker.getState());
        assertEquals(0.0, breaker.getFailureRate());
        assertTrue(breaker.tryAcquirePermission());
    }

    @Test
    @DisplayName("Constructor argument validations")
    void testValidations() {
        assertThrows(NullPointerException.class, () -> new TimeWindowCircuitBreaker(null, 50, Duration.ofSeconds(5)));
        assertThrows(NullPointerException.class, () -> new TimeWindowCircuitBreaker(Duration.ofSeconds(10), 50, null));
        assertThrows(IllegalArgumentException.class, () -> new TimeWindowCircuitBreaker(Duration.ZERO, 50, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new TimeWindowCircuitBreaker(Duration.ofSeconds(10), 0, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new TimeWindowCircuitBreaker(Duration.ofSeconds(10), 101, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new TimeWindowCircuitBreaker(Duration.ofSeconds(10), 50, Duration.ZERO));
    }
}
