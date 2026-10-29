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

class DebouncerTest {

    private Debouncer debouncer;

    @BeforeEach
    void setUp() {
        debouncer = new Debouncer();
    }

    @AfterEach
    void tearDown() {
        debouncer.close();
    }

    @Test
    @DisplayName("Trailing mode: rapid successive calls should execute only the latest action")
    void trailingDebounceExecutesOnlyLatest() throws InterruptedException {
        AtomicReference<String> result = new AtomicReference<>();
        AtomicInteger runCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 1; i <= 5; i++) {
            final String val = "value-" + i;
            debouncer.debounce("test-key", Duration.ofMillis(80), () -> {
                result.set(val);
                runCount.incrementAndGet();
                latch.countDown();
            });
            Thread.sleep(15);
        }

        assertTrue(debouncer.hasPending("test-key"));
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        // Give small buffer for any unexpected trailing calls
        Thread.sleep(120);

        assertEquals("value-5", result.get());
        assertEquals(1, runCount.get());
        assertFalse(debouncer.hasPending("test-key"));
    }

    @Test
    @DisplayName("Trailing mode: cancel should prevent scheduled action from executing")
    void cancelPreventsExecution() throws InterruptedException {
        AtomicInteger runCount = new AtomicInteger(0);

        debouncer.debounce("cancel-key", Duration.ofMillis(100), runCount::incrementAndGet);
        assertTrue(debouncer.hasPending("cancel-key"));

        boolean cancelled = debouncer.cancel("cancel-key");
        assertTrue(cancelled);
        assertFalse(debouncer.hasPending("cancel-key"));

        Thread.sleep(150);
        assertEquals(0, runCount.get());
    }

    @Test
    @DisplayName("Leading mode: first call executes immediately, quiet period suppresses subsequent calls")
    void leadingDebounceExecutesImmediatelyAndResetsQuietPeriod() throws InterruptedException {
        AtomicInteger runCount = new AtomicInteger(0);

        // First call should run immediately
        debouncer.debounce("lead-key", Duration.ofMillis(100), DebounceMode.LEADING, runCount::incrementAndGet);
        assertEquals(1, runCount.get(), "Leading call must execute synchronously on first trigger");

        // Subsequent call within quiet period must be suppressed
        debouncer.debounce("lead-key", Duration.ofMillis(100), DebounceMode.LEADING, runCount::incrementAndGet);
        assertEquals(1, runCount.get(), "Subsequent call during quiet period must be suppressed");

        // Wait beyond quiet period
        Thread.sleep(150);

        // Now next call should run immediately again
        debouncer.debounce("lead-key", Duration.ofMillis(100), DebounceMode.LEADING, runCount::incrementAndGet);
        assertEquals(2, runCount.get(), "Call after quiet period must execute immediately");
    }

    @Test
    @DisplayName("Distinct keys should run independently")
    void distinctKeysRunIndependently() throws InterruptedException {
        AtomicInteger countA = new AtomicInteger(0);
        AtomicInteger countB = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        debouncer.debounce("key-A", Duration.ofMillis(60), () -> {
            countA.incrementAndGet();
            latch.countDown();
        });
        debouncer.debounce("key-B", Duration.ofMillis(60), () -> {
            countB.incrementAndGet();
            latch.countDown();
        });

        assertEquals(2, debouncer.pendingCount());
        assertTrue(latch.await(400, TimeUnit.MILLISECONDS));

        assertEquals(1, countA.get());
        assertEquals(1, countB.get());
    }

    @Test
    @DisplayName("Validation and illegal state tests")
    void validationAndState() {
        assertThrows(NullPointerException.class, () -> debouncer.debounce(null, Duration.ofMillis(10), () -> {}));
        assertThrows(NullPointerException.class, () -> debouncer.debounce("k", null, () -> {}));
        assertThrows(NullPointerException.class, () -> debouncer.debounce("k", Duration.ofMillis(10), (Runnable) null));
        assertThrows(IllegalArgumentException.class, () -> debouncer.debounce("k", Duration.ofMillis(-1), () -> {}));

        debouncer.close();
        assertTrue(debouncer.isClosed());
        assertThrows(IllegalStateException.class, () -> debouncer.debounce("k", Duration.ofMillis(10), () -> {}));
    }
}
