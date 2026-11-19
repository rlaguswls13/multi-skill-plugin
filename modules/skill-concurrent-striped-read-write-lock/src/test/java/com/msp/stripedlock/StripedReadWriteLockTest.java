package com.msp.stripedlock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StripedReadWriteLockTest {

    @Test
    @DisplayName("Stripe count rounds up to the next power of 2 and validates bounds")
    void stripeCountPowerOfTwoAndValidation() {
        assertEquals(1, StripedReadWriteLock.create(1).stripeCount());
        assertEquals(2, StripedReadWriteLock.create(2).stripeCount());
        assertEquals(4, StripedReadWriteLock.create(3).stripeCount());
        assertEquals(16, StripedReadWriteLock.create(10).stripeCount());
        assertEquals(64, StripedReadWriteLock.create(64).stripeCount());

        StripedReadWriteLock<String> fairLock = StripedReadWriteLock.create(8, true);
        assertTrue(fairLock.isFair());
        assertEquals(8, fairLock.stripeCount());

        assertThrows(IllegalArgumentException.class, () -> StripedReadWriteLock.create(0));
        assertThrows(IllegalArgumentException.class, () -> StripedReadWriteLock.create(-5));
        assertThrows(IllegalArgumentException.class, () -> StripedReadWriteLock.create(StripedReadWriteLock.MAX_STRIPES + 1));
    }

    @Test
    @DisplayName("Avalanche hashing distributes sequential integer keys across stripes")
    void avalancheHashingDistribution() {
        StripedReadWriteLock<Integer> striped = StripedReadWriteLock.create(32);
        int[] counts = new int[striped.stripeCount()];
        for (int i = 0; i < 3200; i++) {
            counts[striped.stripeIndex(i)]++;
        }
        int nonEmpty = 0;
        for (int c : counts) {
            if (c > 0) nonEmpty++;
        }
        assertEquals(32, nonEmpty, "All 32 stripes should be populated");
    }

    @Test
    @DisplayName("withReadLock and withWriteLock guard state and release locks on exception")
    void singleKeyReadWriteOperations() {
        StripedReadWriteLock<String> locks = StripedReadWriteLock.create(16);
        Map<String, Integer> store = new HashMap<>();

        locks.withWriteLock("acct-1", () -> store.put("acct-1", 100));
        int balance = locks.withReadLock("acct-1", () -> store.get("acct-1"));
        assertEquals(100, balance);

        assertThrows(IllegalStateException.class, () ->
                locks.withWriteLock("acct-1", () -> {
                    throw new IllegalStateException("boom");
                })
        );

        // Lock must have been released after exception
        assertTrue(locks.writeLock("acct-1").tryLock());
        locks.writeLock("acct-1").unlock();
    }

    @Test
    @DisplayName("Concurrent readers on the same key can enter simultaneously")
    void concurrentReadersSameKey() throws Exception {
        StripedReadWriteLock<String> locks = StripedReadWriteLock.create(16);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> reader = () -> locks.withReadLock("shared-key", () -> {
                bothEntered.countDown();
                try {
                    return release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            });

            Future<Boolean> f1 = pool.submit(reader);
            Future<Boolean> f2 = pool.submit(reader);

            assertTrue(bothEntered.await(10, TimeUnit.SECONDS), "Both readers must hold the read lock concurrently");
            release.countDown();
            assertTrue(f1.get(10, TimeUnit.SECONDS));
            assertTrue(f2.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("Deadlock-free bulkWriteLock preserves total balance under opposing concurrent transfers")
    void deadlockFreeBulkWriteLock() throws Exception {
        StripedReadWriteLock<String> locks = StripedReadWriteLock.create(64);
        Map<String, AtomicInteger> accounts = Map.of(
                "A", new AtomicInteger(1000),
                "B", new AtomicInteger(1000),
                "C", new AtomicInteger(1000),
                "D", new AtomicInteger(1000)
        );
        List<String> keys = List.of("A", "B", "C", "D");

        int threads = 8;
        int iterations = 300;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                tasks.add(() -> {
                    for (int i = 0; i < iterations; i++) {
                        String from = keys.get((tid + i) % keys.size());
                        String to = keys.get((tid + i + 1 + (i % 2)) % keys.size());
                        if (from.equals(to)) {
                            continue;
                        }
                        // Deliberately pass keys in opposing orders across threads
                        List<String> pair = (tid % 2 == 0) ? List.of(from, to) : List.of(to, from);
                        locks.withBulkWriteLock(pair, () -> {
                            int balFrom = accounts.get(from).get();
                            if (balFrom >= 10) {
                                accounts.get(from).set(balFrom - 10);
                                accounts.get(to).set(accounts.get(to).get() + 10);
                            }
                        });
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        }

        int total = locks.withBulkReadLock(keys, () ->
                accounts.values().stream().mapToInt(AtomicInteger::get).sum()
        );
        assertEquals(4000, total);
    }

    @Test
    @DisplayName("BulkLockHandle deduplicates keys, handles empty collections, and close() is idempotent")
    void bulkLockHandleEdgeCases() {
        StripedReadWriteLock<String> locks = StripedReadWriteLock.create(16);

        try (BulkLockHandle empty = locks.bulkWriteLock(List.of())) {
            assertEquals(0, empty.lockedStripeCount());
            assertTrue(empty.isLocked());
        }

        BulkLockHandle dup = locks.bulkWriteLock(List.of("same", "same", "same"));
        assertEquals(1, dup.lockedStripeCount());
        assertTrue(dup.isLocked());
        dup.unlock();
        assertFalse(dup.isLocked());
        // Second unlock must be a safe no-op
        assertDoesNotThrow(dup::unlock);
        assertDoesNotThrow(dup::close);

        assertThrows(NullPointerException.class, () -> locks.readLock(null));
        assertThrows(NullPointerException.class, () -> locks.bulkWriteLock(null));
    }
}
