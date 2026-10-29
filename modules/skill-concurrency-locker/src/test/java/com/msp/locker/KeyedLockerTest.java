package com.msp.locker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KeyedLocker Tests")
class KeyedLockerTest {

    @Nested
    @DisplayName("RefCountKeyedLocker Tests")
    class RefCountTests {

        @Test
        @DisplayName("Operations on same key must execute sequentially (mutual exclusion)")
        void mutualExclusionOnSameKey() throws InterruptedException {
            KeyedLocker<String> locker = KeyedLockers.create();
            String key = "account-123";

            int threadCount = 10;
            int incrementsPerThread = 50;
            int[] sharedCounter = new int[1]; // Not atomic, tests thread safety of lock

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < incrementsPerThread; j++) {
                            locker.withLock(key, () -> {
                                sharedCounter[0]++;
                            });
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(threadCount * incrementsPerThread, sharedCounter[0]);
            // Verify all locks were removed after completion (zero memory leak!)
            assertEquals(0, locker.activeLockCount());
        }

        @Test
        @DisplayName("Operations on different keys should run concurrently in parallel")
        void concurrencyOnDifferentKeys() throws InterruptedException {
            KeyedLocker<String> locker = KeyedLockers.create();

            CountDownLatch keyALatch = new CountDownLatch(1);
            CountDownLatch keyBStartedLatch = new CountDownLatch(1);
            CountDownLatch bothDoneLatch = new CountDownLatch(2);

            long startTime = System.currentTimeMillis();

            // Thread 1 holds "key-A" for 200ms
            new Thread(() -> {
                locker.withLock("key-A", () -> {
                    try {
                        keyALatch.countDown();
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                bothDoneLatch.countDown();
            }).start();

            // Thread 2 acquires "key-B" while Thread 1 is holding "key-A"
            new Thread(() -> {
                try {
                    keyALatch.await();
                    locker.withLock("key-B", () -> {
                        keyBStartedLatch.countDown();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                bothDoneLatch.countDown();
            }).start();

            // Thread 2 should start and finish while Thread 1 is still sleeping in "key-A"
            assertTrue(keyBStartedLatch.await(100, TimeUnit.MILLISECONDS), "Thread 2 on key-B was blocked by key-A!");
            assertTrue(bothDoneLatch.await(2, TimeUnit.SECONDS));

            assertEquals(0, locker.activeLockCount());
        }

        @Test
        @DisplayName("Should throw LockTimeoutException when lock cannot be acquired within timeout")
        void lockTimeout() throws InterruptedException {
            KeyedLocker<String> locker = KeyedLockers.create();
            String key = "resource-99";

            CountDownLatch lockedLatch = new CountDownLatch(1);
            CountDownLatch releaseLatch = new CountDownLatch(1);

            // Thread 1 holds lock
            new Thread(() -> {
                locker.withLock(key, () -> {
                    lockedLatch.countDown();
                    try {
                        releaseLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }).start();

            lockedLatch.await();

            // Thread 2 attempts with short timeout
            LockTimeoutException ex = assertThrows(LockTimeoutException.class, () ->
                    locker.withLock(key, Duration.ofMillis(50), () -> "fail")
            );

            assertEquals(key, ex.getKey());
            assertEquals(Duration.ofMillis(50), ex.getTimeout());

            // Release Thread 1
            releaseLatch.countDown();

            // Wait until Thread 1 finishes
            Thread.sleep(50);
            assertEquals(0, locker.activeLockCount());
        }

        @Test
        @DisplayName("Exception inside action must guarantee lock release and cleanup")
        void exceptionReleasesLock() {
            KeyedLocker<String> locker = KeyedLockers.create();
            String key = "error-key";

            assertThrows(IllegalStateException.class, () ->
                    locker.withLock(key, () -> {
                        throw new IllegalStateException("Action failed!");
                    })
            );

            assertFalse(locker.isLocked(key));
            assertEquals(0, locker.activeLockCount());

            // Key should be immediately lockable again
            String result = locker.withLock(key, () -> "recovered");
            assertEquals("recovered", result);
            assertEquals(0, locker.activeLockCount());
        }

        @Test
        @DisplayName("Automatic garbage collection: activeLockCount becomes 0 for thousands of distinct keys")
        void zeroMemoryLeakStress() {
            KeyedLocker<Integer> locker = KeyedLockers.create();

            for (int i = 0; i < 5000; i++) {
                final int key = i;
                locker.withLock(key, () -> key * 2);
            }

            assertEquals(0, locker.activeLockCount(), "Unused locks must be garbage-collected immediately");
        }
    }

    @Nested
    @DisplayName("StripedKeyedLocker Tests")
    class StripedLockerTests {

        @Test
        @DisplayName("Striped locker provides mutual exclusion and executes actions")
        void stripedMutualExclusion() {
            KeyedLocker<String> locker = KeyedLockers.striped(16);

            String result = locker.withLock("order-42", () -> "success");
            assertEquals("success", result);
        }

        @Test
        @DisplayName("Striped locker handles timeout")
        void stripedTimeout() throws InterruptedException {
            KeyedLocker<String> locker = KeyedLockers.striped(4);
            String key = "shared-stripe-key";

            CountDownLatch held = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            new Thread(() -> {
                locker.withLock(key, () -> {
                    held.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }).start();

            held.await();

            assertThrows(LockTimeoutException.class, () ->
                    locker.withLock(key, Duration.ofMillis(30), () -> "failed")
            );

            release.countDown();
        }
    }
}
