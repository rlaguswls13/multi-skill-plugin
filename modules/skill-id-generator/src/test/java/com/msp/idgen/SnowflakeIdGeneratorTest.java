package com.msp.idgen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("Snowflake should generate positive IDs and parse components correctly")
    void testBasicGenerationAndParsing() {
        long nodeId = 42L;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(nodeId);

        long beforeMs = System.currentTimeMillis();
        long id = generator.nextId();
        long afterMs = System.currentTimeMillis();

        assertTrue(id > 0, "ID must be positive");
        assertEquals(nodeId, generator.extractNodeId(id), "Extracted node ID must match");

        long extractedMs = generator.extractTimestamp(id);
        assertTrue(extractedMs >= beforeMs && extractedMs <= afterMs,
                "Extracted timestamp must be within execution window");

        Instant instant = generator.extractInstant(id);
        assertEquals(extractedMs, instant.toEpochMilli());
    }

    @Test
    @DisplayName("Snowflake with datacenter and worker ID correctly encodes node ID")
    void testDatacenterAndWorkerId() {
        long dcId = 3L; // bits 5-9
        long workerId = 7L; // bits 0-4
        long expectedNodeId = (dcId << 5) | workerId;

        SnowflakeIdGenerator generator = SnowflakeIdGenerator.of(dcId, workerId);
        assertEquals(expectedNodeId, generator.getNodeId());

        long id = generator.nextId();
        assertEquals(expectedNodeId, generator.extractNodeId(id));
    }

    @Test
    @DisplayName("Snowflake IDs generated sequentially should be strictly monotonically increasing")
    void testMonotonicity() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(10L);
        long prev = 0L;
        for (int i = 0; i < 5000; i++) {
            long next = generator.nextId();
            assertTrue(next > prev, "Next ID must be strictly greater than previous");
            prev = next;
        }
    }

    @Test
    @DisplayName("Snowflake IDs must be completely unique under high concurrent multithreading")
    void testMultithreadedUniqueness() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(5L);
        int threadCount = 10;
        int idsPerThread = 1000;
        int totalExpectedIds = threadCount * idsPerThread;

        Set<Long> generatedIds = ConcurrentHashMap.newKeySet(totalExpectedIds);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < idsPerThread; i++) {
                        generatedIds.add(generator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timeout waiting for threads");
        executor.shutdown();

        assertEquals(totalExpectedIds, generatedIds.size(), "All IDs across threads must be unique");
    }

    @Test
    @DisplayName("Snowflake should throw ClockMovedBackwardsException when clock moves back beyond threshold")
    void testClockMovedBackwardsDefense() {
        AtomicLong simulatedClock = new AtomicLong(100_000L);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L, 0L, 5L, simulatedClock::get);

        long id1 = generator.nextId();
        assertTrue(id1 > 0);

        // Move clock backward by 10ms (exceeds 5ms threshold)
        simulatedClock.set(99_990L);

        ClockMovedBackwardsException ex = assertThrows(
                ClockMovedBackwardsException.class,
                generator::nextId
        );
        assertEquals(99_990L, ex.getCurrentTimestamp());
        assertEquals(100_000L, ex.getLastTimestamp());
    }

    @Test
    @DisplayName("Snowflake should wait when clock drift is within maxBackwardDriftMs")
    void testClockSmallDriftRecovery() {
        AtomicLong simulatedClock = new AtomicLong(100_000L);
        // drift is 2ms, which is <= 5ms max drift
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L, 0L, 5L, () -> {
            long current = simulatedClock.get();
            // Automatically advance clock after reading backward time
            if (current == 99_998L) {
                simulatedClock.set(100_001L);
            }
            return current;
        });

        generator.nextId();
        simulatedClock.set(99_998L); // 2ms backwards

        // Should recover without exception
        long id = generator.nextId();
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("Invalid constructor arguments should throw IllegalArgumentException")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1024L));
        assertThrows(IllegalArgumentException.class, () -> SnowflakeIdGenerator.of(32L, 0L));
        assertThrows(IllegalArgumentException.class, () -> SnowflakeIdGenerator.of(0L, 32L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1L, -100L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1L, 0L, -1L, System::currentTimeMillis));
    }
}
