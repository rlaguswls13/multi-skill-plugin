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

class TsidGeneratorTest {

    @Test
    @DisplayName("TSID string must be 13 characters and roundtrip correctly to long")
    void testRoundtripEncoding() {
        TsidGenerator generator = new TsidGenerator(7);

        for (int i = 0; i < 1000; i++) {
            long rawLong = generator.nextLong();
            assertTrue(rawLong > 0, "TSID long must be positive");

            String base32 = TsidGenerator.toBase32(rawLong);
            assertEquals(13, base32.length(), "TSID string must be exactly 13 characters");

            long decoded = TsidGenerator.fromBase32(base32);
            assertEquals(rawLong, decoded, "Decoded TSID long must match original");
        }
    }

    @Test
    @DisplayName("TSID generator nextTsid produces valid 13-character string")
    void testNextTsid() {
        TsidGenerator generator = new TsidGenerator();
        String tsid = generator.nextTsid();

        assertNotNull(tsid);
        assertEquals(13, tsid.length());
        long decoded = TsidGenerator.fromBase32(tsid);
        assertTrue(decoded > 0);
    }

    @Test
    @DisplayName("Crockford Base32 decoding should be case-insensitive and handle aliases")
    void testLenientDecoding() {
        // Test lower-case decoding
        long val = 1234567890123456L;
        String encoded = TsidGenerator.toBase32(val);
        long decodedLower = TsidGenerator.fromBase32(encoded.toLowerCase());
        assertEquals(val, decodedLower);

        // Test alias replacement: 'O'/'o' -> 0, 'I'/'i'/'L'/'l' -> 1
        assertEquals(TsidGenerator.fromBase32("0100000000000"), TsidGenerator.fromBase32("OIOOOOOOOOOOO"));
        assertEquals(TsidGenerator.fromBase32("0100000000000"), TsidGenerator.fromBase32("oiooooooooooo"));
        assertEquals(TsidGenerator.fromBase32("0000000000001"), TsidGenerator.fromBase32("ooooooooooool"));
        assertEquals(TsidGenerator.fromBase32("0000000000001"), TsidGenerator.fromBase32("OOOOOOOOOOOOL"));
    }

    @Test
    @DisplayName("Invalid TSID strings must throw IllegalArgumentException")
    void testInvalidDecoding() {
        assertThrows(IllegalArgumentException.class, () -> TsidGenerator.fromBase32("TOO_SHORT"));
        assertThrows(IllegalArgumentException.class, () -> TsidGenerator.fromBase32("WAY_TOO_LONG_STRING_OVER_13"));
        assertThrows(IllegalArgumentException.class, () -> TsidGenerator.fromBase32("0123456789ABC!")); // invalid char '!'
    }

    @Test
    @DisplayName("TSIDs generated in parallel must be completely unique")
    void testMultithreadedUniqueness() throws InterruptedException {
        TsidGenerator generator = new TsidGenerator(42);
        int threadCount = 10;
        int countPerThread = 1000;
        int totalExpected = threadCount * countPerThread;

        Set<String> set = ConcurrentHashMap.newKeySet(totalExpected);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < countPerThread; j++) {
                        set.add(generator.nextTsid());
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
}
