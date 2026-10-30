package com.msp.cronjitter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CronJitterTest {

    @Test
    @DisplayName("Random jitter within [0, maxJitter]")
    void testRandomJitter() {
        Instant base = Instant.parse("2026-10-31T00:00:00Z");
        Duration maxJitter = Duration.ofSeconds(30);

        for (int i = 0; i < 20; i++) {
            Instant jittered = CronJitter.applyRandomJitter(base, maxJitter);
            assertTrue(!jittered.isBefore(base));
            assertTrue(!jittered.isAfter(base.plus(maxJitter)));
        }
    }

    @Test
    @DisplayName("Symmetric jitter within [-maxJitter, +maxJitter]")
    void testSymmetricJitter() {
        Instant base = Instant.parse("2026-10-31T00:00:00Z");
        Duration maxJitter = Duration.ofSeconds(10);

        for (int i = 0; i < 20; i++) {
            Instant jittered = CronJitter.applySymmetricJitter(base, maxJitter);
            assertTrue(!jittered.isBefore(base.minus(maxJitter)));
            assertTrue(!jittered.isAfter(base.plus(maxJitter)));
        }
    }

    @Test
    @DisplayName("Deterministic jitter provides stable offset for same key")
    void testDeterministicJitter() {
        Instant base = Instant.parse("2026-10-31T00:00:00Z");
        Duration maxJitter = Duration.ofMinutes(5);

        Instant j1 = CronJitter.applyDeterministicJitter(base, "server-node-1", maxJitter);
        Instant j2 = CronJitter.applyDeterministicJitter(base, "server-node-1", maxJitter);
        Instant jOther = CronJitter.applyDeterministicJitter(base, "server-node-99", maxJitter);

        assertEquals(j1, j2);
        assertTrue(!j1.isBefore(base));
        assertTrue(!j1.isAfter(base.plus(maxJitter)));
    }
}
