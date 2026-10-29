package com.msp.slowquery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SlowQueryTrackerTest {

    @Test
    @DisplayName("Query execution metrics aggregation across normalized variants")
    void testStatsAggregation() {
        SlowQueryTracker tracker = new SlowQueryTracker(Duration.ofMillis(300));

        tracker.recordQuery("SELECT * FROM users WHERE id = 1", Duration.ofMillis(100));
        tracker.recordQuery("SELECT * FROM users WHERE id = 2", Duration.ofMillis(200));
        tracker.recordQuery("SELECT * FROM users WHERE id = 3", Duration.ofMillis(400)); // slow

        Optional<QueryStats> optStats = tracker.getStats("SELECT * FROM users WHERE id = 999");
        assertTrue(optStats.isPresent());
        QueryStats stats = optStats.get();

        assertEquals(3, stats.executionCount());
        assertEquals(1, stats.slowCount());
        assertEquals(Duration.ofMillis(700), stats.totalDuration());
        assertEquals(Duration.ofMillis(100), stats.minDuration());
        assertEquals(Duration.ofMillis(400), stats.maxDuration());
        assertEquals(233, stats.avgDuration().toMillis());
        assertEquals(1.0 / 3.0, stats.slowRatio(), 0.01);
    }

    @Test
    @DisplayName("Alert listener callback triggers when query exceeds threshold")
    void testSlowQueryAlertListener() {
        SlowQueryTracker tracker = new SlowQueryTracker(Duration.ofMillis(250));
        List<SlowQueryAlert> alerts = Collections.synchronizedList(new ArrayList<>());
        tracker.addListener(alerts::add);

        // Fast query (under threshold)
        tracker.recordQuery("SELECT count(*) FROM items WHERE cat = 'books'", Duration.ofMillis(50));
        assertEquals(0, alerts.size());

        // Slow query (exceeds threshold)
        tracker.recordQuery("SELECT count(*) FROM items WHERE cat = 'electronics'", Duration.ofMillis(350));
        assertEquals(1, alerts.size());

        SlowQueryAlert alert = alerts.get(0);
        assertEquals("SELECT count(*) FROM items WHERE cat = 'electronics'", alert.rawSql());
        assertEquals("SELECT count(*) FROM items WHERE cat = ?", alert.normalizedSql());
        assertEquals(Duration.ofMillis(350), alert.duration());
        assertEquals(Duration.ofMillis(250), alert.threshold());
    }

    @Test
    @DisplayName("Execute Callable and Runnable measurement")
    void testExecuteHelpers() throws Exception {
        SlowQueryTracker tracker = new SlowQueryTracker(Duration.ofMillis(1000));

        String result = tracker.execute("SELECT name FROM config WHERE id = 1", () -> "database-val");
        assertEquals("database-val", result);

        tracker.execute("UPDATE config SET val = 'updated' WHERE id = 1", () -> {
            // simulate fast op
        });

        assertEquals(2, tracker.getAllStats().size());
    }

    @Test
    @DisplayName("AutoCloseable QueryTimer measures block elapsed duration")
    void testQueryTimer() {
        SlowQueryTracker tracker = new SlowQueryTracker(Duration.ofMillis(1000));

        try (SlowQueryTracker.QueryTimer timer = tracker.start("SELECT * FROM large_table")) {
            // simulated query work
        }

        Optional<QueryStats> stats = tracker.getStats("SELECT * FROM large_table");
        assertTrue(stats.isPresent());
        assertEquals(1, stats.get().executionCount());
    }

    @Test
    @DisplayName("Get top slowest queries ordered by average duration descending")
    void testSlowestQueriesOrdering() {
        SlowQueryTracker tracker = new SlowQueryTracker(Duration.ofMillis(500));

        tracker.recordQuery("SELECT * FROM tbl_fast WHERE id = 1", Duration.ofMillis(10));
        tracker.recordQuery("SELECT * FROM tbl_med WHERE id = 1", Duration.ofMillis(200));
        tracker.recordQuery("SELECT * FROM tbl_slow WHERE id = 1", Duration.ofMillis(800));

        List<QueryStats> slowest = tracker.getSlowestQueries(2);
        assertEquals(2, slowest.size());
        assertEquals("SELECT * FROM tbl_slow WHERE id = ?", slowest.get(0).normalizedSql());
        assertEquals("SELECT * FROM tbl_med WHERE id = ?", slowest.get(1).normalizedSql());
    }

    @Test
    @DisplayName("Concurrent recording safety")
    void testConcurrentRecording() throws Exception {
        SlowQueryTracker tracker = new SlowQueryTracker(Duration.ofMillis(100));
        int threadCount = 10;
        int queriesPerThread = 50;
        var executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < queriesPerThread; j++) {
                        tracker.recordQuery("SELECT * FROM users WHERE group_id = " + threadId + " AND user_id = " + j,
                                Duration.ofMillis(10 + (j % 5)));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        QueryStats stats = tracker.getStats("SELECT * FROM users WHERE group_id = 0 AND user_id = 0").orElseThrow();
        assertEquals(threadCount * queriesPerThread, stats.executionCount());
    }
}
