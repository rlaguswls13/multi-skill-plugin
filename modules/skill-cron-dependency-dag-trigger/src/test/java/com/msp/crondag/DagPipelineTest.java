package com.msp.crondag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DagPipelineTest {

    @Test
    @DisplayName("Should execute linear DAG in topological order with context sharing")
    void testLinearDag() {
        DagPipeline pipeline = DagPipeline.builder()
                .addTask(DagTask.of("fetchUser", ctx -> "Alice"))
                .addTask(DagTask.of("fetchOrder", Set.of("fetchUser"), ctx -> {
                    String user = ctx.getResult("fetchUser");
                    return "Order-for-" + user;
                }))
                .addTask(DagTask.of("sendReceipt", Set.of("fetchOrder"), ctx -> {
                    String order = ctx.getResult("fetchOrder");
                    return "Sent:" + order;
                }))
                .build();

        List<String> order = pipeline.topologicalSort();
        assertEquals(List.of("fetchUser", "fetchOrder", "sendReceipt"), order);

        DagExecutionReport report = pipeline.execute();

        assertTrue(report.isAllSuccess());
        assertEquals("Alice", report.get("fetchUser").value());
        assertEquals("Order-for-Alice", report.get("fetchOrder").value());
        assertEquals("Sent:Order-for-Alice", report.get("sendReceipt").value());
    }

    @Test
    @DisplayName("Should execute diamond DAG with parallel branch execution")
    void testDiamondDag() {
        // A -> (B, C) -> D
        DagPipeline pipeline = DagPipeline.builder()
                .addTask(DagTask.of("A", ctx -> 10))
                .addTask(DagTask.of("B", Set.of("A"), ctx -> {
                    int a = ctx.getResult("A");
                    return a * 2; // 20
                }))
                .addTask(DagTask.of("C", Set.of("A"), ctx -> {
                    int a = ctx.getResult("A");
                    return a + 5; // 15
                }))
                .addTask(DagTask.of("D", Set.of("B", "C"), ctx -> {
                    int b = ctx.getResult("B");
                    int c = ctx.getResult("C");
                    return b + c; // 35
                }))
                .build();

        DagExecutionReport report = pipeline.execute();

        assertTrue(report.isAllSuccess());
        assertEquals(35, (Integer) report.get("D").value());
    }

    @Test
    @DisplayName("Should detect cyclic dependency and throw CyclicDependencyException")
    void testCycleDetection() {
        // A -> B -> C -> A
        DagPipeline.Builder builder = DagPipeline.builder()
                .addTask(DagTask.of("A", Set.of("C"), ctx -> "A"))
                .addTask(DagTask.of("B", Set.of("A"), ctx -> "B"))
                .addTask(DagTask.of("C", Set.of("B"), ctx -> "C"));

        CyclicDependencyException ex = assertThrows(CyclicDependencyException.class, builder::build);
        assertNotNull(ex.getCyclePath());
        assertTrue(ex.getMessage().contains("Cyclic dependency detected"));
    }

    @Test
    @DisplayName("Should skip downstream task when upstream task fails while allowing independent tasks to complete")
    void testUpstreamFailureHandling() {
        AtomicInteger executedCount = new AtomicInteger(0);

        DagPipeline pipeline = DagPipeline.builder()
                .addTask(DagTask.of("faultyRoot", ctx -> {
                    executedCount.incrementAndGet();
                    throw new IllegalStateException("Database connection down");
                }))
                .addTask(DagTask.of("downstreamWorker", Set.of("faultyRoot"), ctx -> {
                    executedCount.incrementAndGet();
                    return "Never run";
                }))
                .addTask(DagTask.of("independentWorker", ctx -> {
                    executedCount.incrementAndGet();
                    return "Independent Success";
                }))
                .build();

        DagExecutionReport report = pipeline.execute();

        assertFalse(report.isAllSuccess());
        assertTrue(report.hasFailedTasks());
        assertTrue(report.hasSkippedTasks());

        // faultyRoot failed
        TaskExecutionResult<?> faulty = report.get("faultyRoot");
        assertTrue(faulty.isFailed());
        assertInstanceOf(IllegalStateException.class, faulty.error());

        // downstreamWorker was skipped
        TaskExecutionResult<?> downstream = report.get("downstreamWorker");
        assertTrue(downstream.isSkipped());
        assertInstanceOf(UpstreamFailedException.class, downstream.error());

        // independentWorker succeeded
        TaskExecutionResult<?> independent = report.get("independentWorker");
        assertTrue(independent.isSuccess());
        assertEquals("Independent Success", independent.value());

        // Only 2 tasks actually executed (faultyRoot + independentWorker)
        assertEquals(2, executedCount.get());
    }

    @Test
    @DisplayName("Should reject task depending on non-existent task ID")
    void testMissingDependency() {
        DagPipeline.Builder builder = DagPipeline.builder()
                .addTask(DagTask.of("taskA", Set.of("unknownTask"), ctx -> "ok"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(ex.getMessage().contains("unknown task"));
    }
}
