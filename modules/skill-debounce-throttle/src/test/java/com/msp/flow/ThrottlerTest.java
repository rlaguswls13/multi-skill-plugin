package com.msp.flow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ThrottlerTest {

    private Throttler throttler;

    @BeforeEach
    void setUp() {
        throttler = new Throttler();
    }

    @AfterEach
    void tearDown() {
        throttler.close();
    }

    @Test
    @DisplayName("Leading throttle: executes first call immediately, drops intermediate calls within window")
    void leadingThrottleLimitsInvocations() throws InterruptedException {
        AtomicInteger runCount = new AtomicInteger(0);
        Duration interval = Duration.ofMillis(80);

        // 1st call -> allowed
        boolean first = throttler.throttle("user:1", interval, runCount::incrementAndGet);
        assertTrue(first);
        assertEquals(1, runCount.get());

        // 2nd and 3rd calls within interval -> suppressed
        boolean second = throttler.throttle("user:1", interval, runCount::incrementAndGet);
        boolean third = throttler.throttle("user:1", interval, runCount::incrementAndGet);
        assertFalse(second);
        assertFalse(third);
        assertEquals(1, runCount.get());

        // Wait beyond interval
        Thread.sleep(110);

        // Next call after interval -> allowed
        boolean fourth = throttler.throttle("user:1", interval, runCount::incrementAndGet);
        assertTrue(fourth);
        assertEquals(2, runCount.get());
    }

    @Test
    @DisplayName("Trailing throttle: executes latest action after interval expires")
    void trailingThrottleExecutesLatestAtWindowEnd() throws InterruptedException {
        AtomicReference<String> result = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        Duration interval = Duration.ofMillis(80);

        for (int i = 1; i <= 4; i++) {
            final String val = "trailing-" + i;
            throttler.throttle("item:1", interval, ThrottleMode.TRAILING, () -> {
                result.set(val);
                count.incrementAndGet();
                latch.countDown();
            });
            Thread.sleep(10);
        }

        assertTrue(throttler.hasPendingTrailing("item:1"));
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        Thread.sleep(100);

        assertEquals("trailing-4", result.get());
        assertEquals(1, count.get());
        assertFalse(throttler.hasPendingTrailing("item:1"));
    }

    @Test
    @DisplayName("Leading and Trailing mode: executes first immediately, runs latest trailing after interval")
    void leadingAndTrailingCombined() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<String> latest = new AtomicReference<>();
        Duration interval = Duration.ofMillis(90);

        // Leading call
        boolean lead = throttler.throttle("combo", interval, ThrottleMode.LEADING_AND_TRAILING, () -> {
            callCount.incrementAndGet();
            latest.set("lead");
        });
        assertTrue(lead);
        assertEquals(1, callCount.get());
        assertEquals("lead", latest.get());

        // Subsequent intermediate calls
        for (int i = 1; i <= 3; i++) {
            final String msg = "sub-" + i;
            throttler.throttle("combo", interval, ThrottleMode.LEADING_AND_TRAILING, () -> {
                callCount.incrementAndGet();
                latest.set(msg);
            });
            Thread.sleep(10);
        }

        // Wait for trailing to fire
        Thread.sleep(130);

        assertEquals(2, callCount.get(), "Total calls should be 2 (1 leading + 1 trailing)");
        assertEquals("sub-3", latest.get(), "Trailing execution should have run the most recent action");
    }

    @Test
    @DisplayName("Reset allows immediate execution without waiting for interval to pass")
    void resetAllowsImmediateExecution() {
        AtomicInteger count = new AtomicInteger(0);
        Duration interval = Duration.ofMillis(500);

        assertTrue(throttler.throttle("k1", interval, count::incrementAndGet));
        assertFalse(throttler.throttle("k1", interval, count::incrementAndGet));

        throttler.reset("k1");

        assertTrue(throttler.throttle("k1", interval, count::incrementAndGet));
        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("Independent keys do not block each other")
    void independentKeysThrottleSeparately() {
        Duration interval = Duration.ofMillis(100);
        AtomicInteger countA = new AtomicInteger(0);
        AtomicInteger countB = new AtomicInteger(0);

        assertTrue(throttler.throttle("keyA", interval, countA::incrementAndGet));
        assertTrue(throttler.throttle("keyB", interval, countB::incrementAndGet));
        assertFalse(throttler.throttle("keyA", interval, countA::incrementAndGet));
        assertFalse(throttler.throttle("keyB", interval, countB::incrementAndGet));

        assertEquals(1, countA.get());
        assertEquals(1, countB.get());
    }
}
