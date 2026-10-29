package com.msp.idgen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NanoIdGeneratorTest {

    @Test
    @DisplayName("Default NanoID must be 21 characters")
    void testDefaultNanoId() {
        String id = NanoIdGenerator.randomNanoId();
        assertNotNull(id);
        assertEquals(21, id.length());
    }

    @Test
    @DisplayName("NanoID with custom length")
    void testCustomLength() {
        for (int len : new int[]{5, 10, 16, 32, 64}) {
            String id = NanoIdGenerator.randomNanoId(len);
            assertEquals(len, id.length());
        }
    }

    @Test
    @DisplayName("NanoID with custom alphabet should only use characters from that alphabet")
    void testCustomAlphabet() {
        char[] hexAlphabet = "0123456789abcdef".toCharArray();
        NanoIdGenerator generator = new NanoIdGenerator(hexAlphabet, 16);

        for (int i = 0; i < 100; i++) {
            String id = generator.generate();
            assertEquals(16, id.length());
            for (char c : id.toCharArray()) {
                assertTrue("0123456789abcdef".indexOf(c) >= 0, "Character must be from alphabet: " + c);
            }
        }
    }

    @Test
    @DisplayName("NanoID multithreaded uniqueness")
    void testMultithreadedUniqueness() throws InterruptedException {
        int threadCount = 8;
        int countPerThread = 1000;
        int totalExpected = threadCount * countPerThread;

        Set<String> set = ConcurrentHashMap.newKeySet(totalExpected);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < countPerThread; j++) {
                        set.add(NanoIdGenerator.randomNanoId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(totalExpected, set.size());
    }

    @Test
    @DisplayName("Invalid parameters should throw IllegalArgumentException")
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> NanoIdGenerator.randomNanoId(0));
        assertThrows(IllegalArgumentException.class, () -> NanoIdGenerator.randomNanoId(-5));
        assertThrows(IllegalArgumentException.class, () -> new NanoIdGenerator(new char[0], 21));
        assertThrows(IllegalArgumentException.class, () -> new NanoIdGenerator(new char[256], 21));
    }
}
