package com.msp.cronjitter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CronJitterTest {

    @Test
    @DisplayName("Full jitter within [0, maxJitter]")
    void testFullJitterBounds() {
        Instant base = Instant.parse("2026-10-31T00:00:00Z");
        Duration maxJitter = Duration.ofSeconds(30);

        for (int i = 0; i < 100; i++) {
            Instant jittered = CronJitterRandomizer.applyJitter(base, maxJitter, JitterStrategy.FULL);
            assertFalse(jittered.isBefore(base));
            assertFalse(jittered.isAfter(base.plus(maxJitter)));
        }
    }

    @Test
    @DisplayName("Equal jitter within [maxJitter/2, maxJitter]")
    void testEqualJitterBounds() {
        Instant base = Instant.parse("2026-10-31T00:00:00Z");
        Duration maxJitter = Duration.ofSeconds(40);
        Instant minExpected = base.plus(Duration.ofSeconds(20));
        Instant maxExpected = base.plus(maxJitter);

        for (int i = 0; i < 100; i++) {
            Instant jittered = CronJitterRandomizer.applyJitter(base, maxJitter, JitterStrategy.EQUAL);
            assertFalse(jittered.isBefore(minExpected), "Jittered time must be >= max/2");
            assertFalse(jittered.isAfter(maxExpected), "Jittered time must be <= max");
        }
    }

    @Test
    @DisplayName("Decorrelated jitter stays within valid bounds")
    void testDecorrelatedJitterBounds() {
        Instant base = Instant.parse("2026-10-31T00:00:00Z");
        Duration maxJitter = Duration.ofSeconds(60);
        Instant minExpected = base.plus(Duration.ofSeconds(20)); // max/3
        Instant maxExpected = base.plus(maxJitter);

        for (int i = 0; i < 100; i++) {
            Instant jittered = CronJitterRandomizer.applyJitter(base, maxJitter, JitterStrategy.DECORRELATED);
            assertFalse(jittered.isBefore(minExpected), "Decorrelated jitter must be >= min bound");
            assertFalse(jittered.isAfter(maxExpected), "Decorrelated jitter must be <= max bound");
        }
    }

    @Test
    @DisplayName("Retry backoff calculation for FULL, EQUAL, and DECORRELATED strategies")
    void testRetryBackoffCalculation() {
        Duration base = Duration.ofMillis(100);
        Duration max = Duration.ofSeconds(10);

        // Attempt 0
        Duration full0 = CronJitterRandomizer.computeBackoffJitter(base, max, 0, JitterStrategy.FULL);
        assertTrue(full0.toMillis() >= 0 && full0.toMillis() <= 100);

        Duration eq0 = CronJitterRandomizer.computeBackoffJitter(base, max, 0, JitterStrategy.EQUAL);
        assertTrue(eq0.toMillis() >= 50 && eq0.toMillis() <= 100);

        Duration decor0 = CronJitterRandomizer.computeBackoffJitter(base, max, 0, JitterStrategy.DECORRELATED);
        assertEquals(100, decor0.toMillis());

        // Attempt 3 (base * 2^3 = 800ms)
        for (int i = 0; i < 50; i++) {
            Duration full3 = CronJitterRandomizer.computeBackoffJitter(base, max, 3, JitterStrategy.FULL);
            assertTrue(full3.toMillis() >= 0 && full3.toMillis() <= 800);

            Duration eq3 = CronJitterRandomizer.computeBackoffJitter(base, max, 3, JitterStrategy.EQUAL);
            assertTrue(eq3.toMillis() >= 400 && eq3.toMillis() <= 800);

            Duration decor3 = CronJitterRandomizer.computeBackoffJitter(base, max, 3, JitterStrategy.DECORRELATED);
            assertTrue(decor3.toMillis() >= 100 && decor3.toMillis() <= max.toMillis());
        }

        // High attempt capped at max
        Duration fullCapped = CronJitterRandomizer.computeBackoffJitter(base, max, 20, JitterStrategy.FULL);
        assertTrue(fullCapped.toMillis() <= max.toMillis());
    }

    @Test
    @DisplayName("Deterministic seed produces repeatable jitter values")
    void testDeterministicSeedRepeatability() {
        Instant base = Instant.parse("2026-10-31T00:00:00Z");
        Duration max = Duration.ofSeconds(60);

        CronJitterRandomizer rand1 = new CronJitterRandomizer(new Random(42L));
        CronJitterRandomizer rand2 = new CronJitterRandomizer(new Random(42L));

        for (int i = 0; i < 10; i++) {
            Instant j1 = rand1.apply(base, max, JitterStrategy.FULL);
            Instant j2 = rand2.apply(base, max, JitterStrategy.FULL);
            assertEquals(j1, j2, "Deterministic seed must yield identical jitter at step " + i);
        }
    }

    @Test
    @DisplayName("CronJitter legacy methods remain functional")
    void testLegacyCronJitter() {
        Instant base = Instant.parse("2026-10-31T00:00:00Z");
        Duration maxJitter = Duration.ofSeconds(10);

        Instant j1 = CronJitter.applyRandomJitter(base, maxJitter);
        assertFalse(j1.isBefore(base));
        assertFalse(j1.isAfter(base.plus(maxJitter)));

        Instant s1 = CronJitter.applySymmetricJitter(base, maxJitter);
        assertFalse(s1.isBefore(base.minus(maxJitter)));
        assertFalse(s1.isAfter(base.plus(maxJitter)));

        Instant d1 = CronJitter.applyDeterministicJitter(base, "server-1", maxJitter);
        Instant d2 = CronJitter.applyDeterministicJitter(base, "server-1", maxJitter);
        assertEquals(d1, d2);
    }
}
