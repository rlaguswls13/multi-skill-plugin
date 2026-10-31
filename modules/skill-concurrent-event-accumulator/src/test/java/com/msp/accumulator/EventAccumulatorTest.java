package com.msp.accumulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class EventAccumulatorTest {

    @Test
    @DisplayName("배치 크기 임계치 도달 시 즉각 플러시 검증")
    void testFlushOnBatchSizeThreshold() {
        List<List<String>> batches = new CopyOnWriteArrayList<>();

        try (EventAccumulator<String> accumulator = new EventAccumulator<>(
                3,
                Duration.ofSeconds(10), // 긴 타임아웃
                batches::add
        )) {
            accumulator.add("item1");
            accumulator.add("item2");
            assertEquals(0, batches.size());
            assertEquals(2, accumulator.getPendingCount());

            // 3번째 아이템 추가 -> 즉시 1개 배치 플러시
            accumulator.add("item3");
            assertEquals(1, batches.size());
            assertEquals(List.of("item1", "item2", "item3"), batches.get(0));
            assertEquals(0, accumulator.getPendingCount());

            accumulator.add("item4");
            assertEquals(1, batches.size());
            assertEquals(1, accumulator.getPendingCount());
        }
    }

    @Test
    @DisplayName("시간 윈도우 경과 시 자동 플러시 검증")
    void testFlushOnTimeWindow() throws InterruptedException {
        List<List<Integer>> batches = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (EventAccumulator<Integer> accumulator = new EventAccumulator<>(
                100, // 큰 배치 크기
                Duration.ofMillis(50), // 50ms 짧은 주기
                batch -> {
                    batches.add(batch);
                    latch.countDown();
                }
        )) {
            accumulator.add(10);
            accumulator.add(20);

            // 시간 경과 대기 (최대 1초 대기)
            boolean flushed = latch.await(1, TimeUnit.SECONDS);
            assertTrue(flushed, "Expected automatic time-based flush");
            assertEquals(1, batches.size());
            assertEquals(List.of(10, 20), batches.get(0));
            assertEquals(0, accumulator.getPendingCount());
        }
    }

    @Test
    @DisplayName("수동 flush() 호출 검증")
    void testManualFlush() {
        List<List<String>> batches = new ArrayList<>();
        try (EventAccumulator<String> accumulator = new EventAccumulator<>(
                10,
                Duration.ofMinutes(1),
                batches::add
        )) {
            accumulator.add("A");
            accumulator.add("B");
            assertEquals(0, batches.size());

            accumulator.flush();
            assertEquals(1, batches.size());
            assertEquals(List.of("A", "B"), batches.get(0));
            assertEquals(0, accumulator.getPendingCount());
        }
    }

    @Test
    @DisplayName("close() 시 잔여 버퍼 플러시 및 종료 후 add() 금지 검증")
    void testCloseFlushesRemainingAndBlocksAdd() {
        List<List<String>> batches = new ArrayList<>();
        EventAccumulator<String> accumulator = new EventAccumulator<>(
                10,
                Duration.ofMinutes(1),
                batches::add
        );

        accumulator.add("residual-1");
        accumulator.add("residual-2");
        assertEquals(0, batches.size());

        accumulator.close();
        assertTrue(accumulator.isClosed());
        assertEquals(1, batches.size());
        assertEquals(List.of("residual-1", "residual-2"), batches.get(0));

        assertThrows(IllegalStateException.class, () -> accumulator.add("after-close"));
    }

    @Test
    @DisplayName("멀티스레드 동시 add 시 데이터 유실 및 중복 없음 검증")
    void testConcurrentAdds() throws InterruptedException {
        int threadCount = 8;
        int itemsPerThread = 500;
        int totalExpected = threadCount * itemsPerThread;

        List<Integer> receivedItems = Collections.synchronizedList(new ArrayList<>());

        try (EventAccumulator<Integer> accumulator = new EventAccumulator<>(
                50,
                Duration.ofMillis(50),
                receivedItems::addAll
        )) {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < itemsPerThread; i++) {
                            accumulator.add(threadId * itemsPerThread + i);
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
        } // close() triggers final flush

        assertEquals(totalExpected, receivedItems.size());
    }

    @Test
    @DisplayName("에러 핸들러 호출 검증")
    void testErrorHandler() {
        AtomicBoolean errorHandled = new AtomicBoolean(false);

        try (EventAccumulator<String> accumulator = new EventAccumulator<>(
                2,
                Duration.ofSeconds(10),
                batch -> {
                    throw new RuntimeException("Simulated consumer failure");
                },
                (throwable, failedBatch) -> {
                    errorHandled.set(true);
                    assertEquals("Simulated consumer failure", throwable.getMessage());
                    assertEquals(2, failedBatch.size());
                }
        )) {
            accumulator.add("fail-1");
            accumulator.add("fail-2");

            assertTrue(errorHandled.get());
        }
    }
}
