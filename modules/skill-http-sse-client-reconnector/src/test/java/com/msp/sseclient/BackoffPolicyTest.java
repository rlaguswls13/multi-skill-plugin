package com.msp.sseclient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BackoffPolicyTest {

    @Test
    @DisplayName("Should exponentially increase backoff delay")
    void testExponentialGrowth() {
        BackoffPolicy policy = new BackoffPolicy(
                Duration.ofMillis(100),
                Duration.ofMillis(10000),
                2.0,
                0.0 // zero jitter for deterministic check
        );

        assertEquals(100, policy.calculateDelay(0, null).toMillis());
        assertEquals(200, policy.calculateDelay(1, null).toMillis());
        assertEquals(400, policy.calculateDelay(2, null).toMillis());
        assertEquals(800, policy.calculateDelay(3, null).toMillis());
    }

    @Test
    @DisplayName("Should cap delay at maxDelay")
    void testMaxDelayCap() {
        BackoffPolicy policy = new BackoffPolicy(
                Duration.ofMillis(500),
                Duration.ofMillis(1200),
                2.0,
                0.0
        );

        assertEquals(500, policy.calculateDelay(0, null).toMillis());
        assertEquals(1000, policy.calculateDelay(1, null).toMillis());
        assertEquals(1200, policy.calculateDelay(2, null).toMillis());
        assertEquals(1200, policy.calculateDelay(10, null).toMillis());
    }

    @Test
    @DisplayName("Should respect SSE retry override")
    void testRetryOverride() {
        BackoffPolicy policy = new BackoffPolicy(
                Duration.ofMillis(100),
                Duration.ofMillis(10000),
                2.0,
                0.0
        );

        // Attempt 0 with 3000ms override
        assertEquals(3000, policy.calculateDelay(0, 3000L).toMillis());
        // Attempt 1 with 3000ms override (3000 * 2 = 6000)
        assertEquals(6000, policy.calculateDelay(1, 3000L).toMillis());
    }

    @Test
    @DisplayName("Jitter bounds stay within expected variance")
    void testJitterBounds() {
        BackoffPolicy policy = new BackoffPolicy(
                Duration.ofMillis(1000),
                Duration.ofMillis(10000),
                2.0,
                0.2 // +/- 20%
        );

        for (int i = 0; i < 50; i++) {
            long delay = policy.calculateDelay(0, null).toMillis();
            assertTrue(delay >= 800 && delay <= 1200, "Delay " + delay + " out of bounds [800, 1200]");
        }
    }
}
