package com.msp.bitmapcounter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class LongBitmapCounterTest {

    @Test
    @DisplayName("기본 set, get, clear 및 isEmpty 동작 검증")
    void testBasicSetGetClear() {
        LongBitmapCounter bitmap = new LongBitmapCounter();
        assertTrue(bitmap.isEmpty());
        assertEquals(0, bitmap.cardinality());

        bitmap.set(0);
        bitmap.set(63);
        bitmap.set(64);
        bitmap.set(127);

        assertFalse(bitmap.isEmpty());
        assertEquals(4, bitmap.cardinality());
        assertTrue(bitmap.get(0));
        assertTrue(bitmap.get(63));
        assertTrue(bitmap.get(64));
        assertTrue(bitmap.get(127));
        assertFalse(bitmap.get(1));
        assertFalse(bitmap.get(65));

        bitmap.clear(63);
        assertEquals(3, bitmap.cardinality());
        assertFalse(bitmap.get(63));
    }

    @Test
    @DisplayName("대규모 bitIndex 설정 시 자동 확장 및 동작 검증")
    void testLargeBitIndexExpansion() {
        LongBitmapCounter bitmap = new LongBitmapCounter(64);
        long largeIndex = 5_000_000L;

        assertFalse(bitmap.get(largeIndex));
        bitmap.set(largeIndex);

        assertTrue(bitmap.get(largeIndex));
        assertEquals(1, bitmap.cardinality());
        assertTrue(bitmap.sizeInBits() > largeIndex);
    }

    @Test
    @DisplayName("setRange 범위 설정 검증 (단일 워드 및 복수 워드 경계)")
    void testSetRange() {
        LongBitmapCounter bitmap = new LongBitmapCounter();

        // 10 ~ 19 (10개 비트)
        bitmap.setRange(10, 20);
        assertEquals(10, bitmap.cardinality());
        for (int i = 10; i < 20; i++) {
            assertTrue(bitmap.get(i));
        }
        assertFalse(bitmap.get(9));
        assertFalse(bitmap.get(20));

        // 여러 워드를 걸치는 큰 범위: 50 ~ 200 (150개 비트)
        bitmap.setRange(50, 200);
        assertEquals(160, bitmap.cardinality()); // 10~19 (10) + 50~199 (150)
        assertTrue(bitmap.get(50));
        assertTrue(bitmap.get(100));
        assertTrue(bitmap.get(199));
        assertFalse(bitmap.get(200));
    }

    @Test
    @DisplayName("DAU/MAU 비트 연산 검증 (AND, OR, XOR, AND-NOT)")
    void testBitwiseOperations() {
        LongBitmapCounter day1 = new LongBitmapCounter();
        LongBitmapCounter day2 = new LongBitmapCounter();

        // Day 1 방문자: 유저 100, 200, 300
        day1.set(100);
        day1.set(200);
        day1.set(300);

        // Day 2 방문자: 유저 200, 300, 400
        day2.set(200);
        day2.set(300);
        day2.set(400);

        // OR: 2일간 총 순방문자(MAU/DAU 합집합) -> 100, 200, 300, 400 (총 4명)
        LongBitmapCounter union = day1.or(day2);
        assertEquals(4, union.cardinality());
        assertTrue(union.get(100));
        assertTrue(union.get(200));
        assertTrue(union.get(300));
        assertTrue(union.get(400));

        // AND: 2일 연속 방문자(Retention) -> 200, 300 (총 2명)
        LongBitmapCounter intersection = day1.and(day2);
        assertEquals(2, intersection.cardinality());
        assertTrue(intersection.get(200));
        assertTrue(intersection.get(300));
        assertFalse(intersection.get(100));
        assertFalse(intersection.get(400));

        // AND-NOT: Day 1에만 방문하고 Day 2에는 오지 않은 유저 (이탈자) -> 100 (총 1명)
        LongBitmapCounter churned = day1.andNot(day2);
        assertEquals(1, churned.cardinality());
        assertTrue(churned.get(100));
        assertFalse(churned.get(200));

        // XOR: 둘 중 하루만 방문한 유저 -> 100, 400 (총 2명)
        LongBitmapCounter symmetricDiff = day1.xor(day2);
        assertEquals(2, symmetricDiff.cardinality());
        assertTrue(symmetricDiff.get(100));
        assertTrue(symmetricDiff.get(400));
    }

    @Test
    @DisplayName("forEachSetBit 비트 순회 검증")
    void testForEachSetBit() {
        LongBitmapCounter bitmap = new LongBitmapCounter();
        bitmap.set(5);
        bitmap.set(67);
        bitmap.set(200);

        List<Long> visited = new ArrayList<>();
        bitmap.forEachSetBit(visited::add);

        assertEquals(List.of(5L, 67L, 200L), visited);
    }

    @Test
    @DisplayName("직렬화 및 역직렬화 (toByteArray, fromByteArray) 검증")
    void testSerialization() {
        LongBitmapCounter original = new LongBitmapCounter();
        original.set(1);
        original.set(65);
        original.set(130);
        original.set(1000);

        byte[] serialized = original.toByteArray();
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        LongBitmapCounter restored = LongBitmapCounter.fromByteArray(serialized);
        assertEquals(original.cardinality(), restored.cardinality());
        assertTrue(restored.get(1));
        assertTrue(restored.get(65));
        assertTrue(restored.get(130));
        assertTrue(restored.get(1000));
        assertFalse(restored.get(999));
    }

    @Test
    @DisplayName("멀티스레드 동시성 안전성 검증")
    void testConcurrency() throws Exception {
        LongBitmapCounter bitmap = new LongBitmapCounter();
        int threads = 8;
        int operations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadOffset = t * operations;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operations; i++) {
                        bitmap.set(threadOffset + i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(finished);
        assertEquals((long) threads * operations, bitmap.cardinality());
    }
}
