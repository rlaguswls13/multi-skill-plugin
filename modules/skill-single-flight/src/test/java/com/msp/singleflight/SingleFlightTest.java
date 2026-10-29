package com.msp.singleflight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SingleFlightTest {

    @Test
    @DisplayName("50 concurrent threads call execute('key1'): callable invoked exactly once, all 50 receive same value")
    void concurrentCallsExecuteOnce() throws Exception {
        SingleFlight<String, String> sf = SingleFlight.create();
        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch inCallableLatch = new CountDownLatch(1);

        List<Future<SingleFlightResult<String>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                startLatch.await();
                return sf.executeWithResult("key1", () -> {
                    callCount.incrementAndGet();
                    inCallableLatch.countDown();
                    Thread.sleep(60); // Hold execution so other threads join in-flight
                    return "database-result-for-key1";
                });
            }));
        }

        startLatch.countDown();
        assertTrue(inCallableLatch.await(2, TimeUnit.SECONDS));

        int leaderCount = 0;
        int sharedCount = 0;

        for (Future<SingleFlightResult<String>> f : futures) {
            SingleFlightResult<String> res = f.get(5, TimeUnit.SECONDS);
            assertEquals("database-result-for-key1", res.value());
            if (res.shared()) {
                sharedCount++;
            } else {
                leaderCount++;
            }
        }

        assertEquals(1, callCount.get(), "Callable should be executed exactly once");
        assertEquals(1, leaderCount, "Exactly 1 thread must be the leader");
        assertEquals(49, sharedCount, "49 threads must have shared the leader's execution");
        assertEquals(0, sf.inFlightCount(), "Cleaned up in-flight entries upon completion");

        pool.shutdown();
    }

    @Test
    @DisplayName("Exception in callable is propagated to all waiting callers and allows subsequent retry")
    void exceptionPropagatedToAllCallers() throws Exception {
        SingleFlight<String, String> sf = SingleFlight.create();
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                startLatch.await();
                return sf.execute("err-key", () -> {
                    callCount.incrementAndGet();
                    Thread.sleep(50);
                    throw new IllegalStateException("Database connection failed");
                });
            }));
        }

        startLatch.countDown();

        for (Future<String> f : futures) {
            Exception ex = assertThrows(Exception.class, () -> f.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof IllegalStateException || ex instanceof IllegalStateException);
            assertTrue(ex.getMessage().contains("Database connection failed") ||
                    (ex.getCause() != null && ex.getCause().getMessage().contains("Database connection failed")));
        }

        assertEquals(1, callCount.get());
        assertEquals(0, sf.inFlightCount());

        // Subsequent call succeeds normally (not stuck in failed state)
        String retryResult = sf.execute("err-key", () -> "recovered-data");
        assertEquals("recovered-data", retryResult);

        pool.shutdown();
    }

    @Test
    @DisplayName("Different keys execute concurrently and independently")
    void differentKeysExecuteIndependently() throws Exception {
        SingleFlight<String, Integer> sf = SingleFlight.create();
        AtomicInteger countA = new AtomicInteger(0);
        AtomicInteger countB = new AtomicInteger(0);

        Integer resA = sf.execute("item:A", () -> {
            countA.incrementAndGet();
            return 100;
        });

        Integer resB = sf.execute("item:B", () -> {
            countB.incrementAndGet();
            return 200;
        });

        assertEquals(100, resA);
        assertEquals(200, resB);
        assertEquals(1, countA.get());
        assertEquals(1, countB.get());
    }

    @Test
    @DisplayName("forget evicts in-flight key so next call starts new execution")
    void forgetEvictsInFlight() throws Exception {
        SingleFlight<String, String> sf = SingleFlight.create();
        CountDownLatch supplierRunning = new CountDownLatch(1);
        CountDownLatch finishSupplier = new CountDownLatch(1);

        CompletableFuture<String> firstCall = CompletableFuture.supplyAsync(() -> {
            try {
                return sf.execute("k1", () -> {
                    supplierRunning.countDown();
                    finishSupplier.await();
                    return "first";
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(supplierRunning.await(2, TimeUnit.SECONDS));
        assertTrue(sf.isInFlight("k1"));

        // Evict k1
        boolean evicted = sf.forget("k1");
        assertTrue(evicted);
        assertFalse(sf.isInFlight("k1"));

        // Second call with same key can now start independent execution
        CompletableFuture<String> secondCall = CompletableFuture.supplyAsync(() -> {
            try {
                return sf.execute("k1", () -> "second");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("second", secondCall.get(2, TimeUnit.SECONDS));

        finishSupplier.countDown();
        assertEquals("first", firstCall.get(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("executeAsync suppresses concurrent async requests")
    void asyncSingleFlight() throws Exception {
        SingleFlight<String, String> sf = SingleFlight.create();
        AtomicInteger asyncRuns = new AtomicInteger(0);

        CompletableFuture<String> cf1 = sf.executeAsync("asyncKey", () -> CompletableFuture.supplyAsync(() -> {
            asyncRuns.incrementAndGet();
            try {
                Thread.sleep(60);
            } catch (InterruptedException ignored) {}
            return "async-val";
        }));

        CompletableFuture<String> cf2 = sf.executeAsync("asyncKey", () -> CompletableFuture.supplyAsync(() -> {
            asyncRuns.incrementAndGet();
            return "async-val-2";
        }));

        assertEquals("async-val", cf1.get(2, TimeUnit.SECONDS));
        assertEquals("async-val", cf2.get(2, TimeUnit.SECONDS));
        assertEquals(1, asyncRuns.get());
    }
}
