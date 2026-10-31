package com.msp.backoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class BackoffCalculatorTest {

    @Test
    @DisplayName("NONE strategy should produce exact deterministic exponential delays capped at maxDelay")
    void testNoJitterExponential() {
        Duration initial = Duration.ofMillis(100);
        Duration max = Duration.ofMillis(1000);

        assertEquals(Duration.ofMillis(100), BackoffCalculator.calculateDelay(0, initial, max, 2.0, JitterStrategy.NONE));
        assertEquals(Duration.ofMillis(200), BackoffCalculator.calculateDelay(1, initial, max, 2.0, JitterStrategy.NONE));
        assertEquals(Duration.ofMillis(400), BackoffCalculator.calculateDelay(2, initial, max, 2.0, JitterStrategy.NONE));
        assertEquals(Duration.ofMillis(800), BackoffCalculator.calculateDelay(3, initial, max, 2.0, JitterStrategy.NONE));
        // Capped at 1000ms
        assertEquals(Duration.ofMillis(1000), BackoffCalculator.calculateDelay(4, initial, max, 2.0, JitterStrategy.NONE));
        assertEquals(Duration.ofMillis(1000), BackoffCalculator.calculateDelay(10, initial, max, 2.0, JitterStrategy.NONE));
    }

    @Test
    @DisplayName("FULL_JITTER strategy should always generate delays between 0 and ceiling")
    void testFullJitterRange() {
        Duration initial = Duration.ofMillis(100);
        Duration max = Duration.ofMillis(1000);

        for (int i = 0; i < 50; i++) {
            Duration delay = BackoffCalculator.calculateDelay(2, initial, max, 2.0, JitterStrategy.FULL_JITTER);
            // Attempt 2 ceiling is 400ms
            assertTrue(delay.toMillis() >= 0 && delay.toMillis() <= 400,
                    "Delay out of range: " + delay.toMillis());
        }
    }

    @Test
    @DisplayName("EQUAL_JITTER strategy should guarantee minimum delay of temp / 2")
    void testEqualJitterRange() {
        Duration initial = Duration.ofMillis(100);
        Duration max = Duration.ofMillis(1000);

        // Attempt 2: temp = 400ms. Half = 200ms. Range should be [200, 400]
        for (int i = 0; i < 50; i++) {
            Duration delay = BackoffCalculator.calculateDelay(2, initial, max, 2.0, JitterStrategy.EQUAL_JITTER);
            assertTrue(delay.toMillis() >= 200 && delay.toMillis() <= 400,
                    "Equal jitter delay out of range: " + delay.toMillis());
        }
    }

    @Test
    @DisplayName("DECORRELATED_JITTER should calculate successive bounded delays")
    void testDecorrelatedJitter() {
        Duration initial = Duration.ofMillis(100);
        Duration max = Duration.ofMillis(2000);
        Duration prev = Duration.ofMillis(200);

        for (int i = 0; i < 50; i++) {
            Duration delay = BackoffCalculator.calculateDecorrelatedDelay(prev, initial, max);
            // Must be between base (100) and min(2000, 200 * 3 = 600)
            assertTrue(delay.toMillis() >= 100 && delay.toMillis() <= 600,
                    "Decorrelated delay out of range: " + delay.toMillis());
        }
    }

    @Test
    @DisplayName("Deterministic RandomGenerator should yield reproducible delays")
    void testDeterministicRandom() {
        Duration initial = Duration.ofMillis(100);
        Duration max = Duration.ofMillis(1000);

        // Fixed mock RandomGenerator returning middle value
        RandomGenerator mockRandom = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 50;
            }

            @Override
            public long nextLong(long origin, long bound) {
                return (origin + bound) / 2;
            }
        };

        Duration delay = BackoffCalculator.calculateDelay(2, initial, max, 2.0, JitterStrategy.FULL_JITTER, mockRandom);
        // temp is 400, origin 0, bound 401 -> (0 + 401)/2 = 200
        assertEquals(Duration.ofMillis(200), delay);
    }

    @Test
    @DisplayName("Should prevent overflow on very high attempt counts")
    void testLargeAttemptOverflowProtection() {
        Duration initial = Duration.ofMillis(500);
        Duration max = Duration.ofSeconds(30);

        Duration delay = BackoffCalculator.calculateDelay(100, initial, max, 2.0, JitterStrategy.NONE);
        assertEquals(max, delay);
    }

    @Test
    @DisplayName("Should validate parameters and throw on invalid multiplier or nulls")
    void testParameterValidation() {
        Duration initial = Duration.ofMillis(100);
        Duration max = Duration.ofSeconds(10);

        assertThrows(IllegalArgumentException.class, () ->
                BackoffCalculator.calculateDelay(1, initial, max, 0.5, JitterStrategy.NONE));

        assertThrows(NullPointerException.class, () ->
                BackoffCalculator.calculateDelay(1, null, max));
    }
}
