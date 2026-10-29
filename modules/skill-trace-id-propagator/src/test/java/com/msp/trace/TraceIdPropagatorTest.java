package com.msp.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdPropagatorTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        TraceIdPropagator.clear();
    }

    @Test
    @DisplayName("Random trace ID and span ID generation")
    void testRandomIdGeneration() {
        String traceId = TraceIdPropagator.randomTraceId();
        assertEquals(32, traceId.length());
        assertTrue(traceId.matches("^[0-9a-f]{32}$"));

        String spanId = TraceIdPropagator.randomSpanId();
        assertEquals(16, spanId.length());
        assertTrue(spanId.matches("^[0-9a-f]{16}$"));
    }

    @Test
    @DisplayName("Current context retrieval and currentOrNew behavior")
    void testCurrentAndCurrentOrNew() {
        assertTrue(TraceIdPropagator.current().isEmpty());

        TraceContext created = TraceIdPropagator.currentOrNew();
        assertNotNull(created);
        assertTrue(TraceIdPropagator.current().isPresent());
        assertEquals(created, TraceIdPropagator.current().get());

        TraceIdPropagator.clear();
        assertTrue(TraceIdPropagator.current().isEmpty());
    }

    @Test
    @DisplayName("AutoCloseable Scope attaches and restores context correctly")
    void testScopeLifecycle() {
        TraceContext ctx1 = TraceContext.create();
        TraceContext ctx2 = TraceContext.create();

        assertTrue(TraceIdPropagator.current().isEmpty());

        try (TraceIdPropagator.Scope scope1 = TraceIdPropagator.open(ctx1)) {
            assertEquals(ctx1, TraceIdPropagator.current().orElseThrow());

            try (TraceIdPropagator.Scope scope2 = TraceIdPropagator.open(ctx2)) {
                assertEquals(ctx2, TraceIdPropagator.current().orElseThrow());
            }

            // After scope2 closes, context returns to ctx1
            assertEquals(ctx1, TraceIdPropagator.current().orElseThrow());
        }

        // After scope1 closes, context returns to empty
        assertTrue(TraceIdPropagator.current().isEmpty());
    }

    @Test
    @DisplayName("Async propagation with wrap(Runnable)")
    void testWrapRunnable() throws Exception {
        TraceContext parent = TraceContext.create();
        TraceIdPropagator.open(parent);

        AtomicReference<TraceContext> backgroundContext = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable wrapped = TraceIdPropagator.wrap(() -> {
            backgroundContext.set(TraceIdPropagator.current().orElse(null));
            latch.countDown();
        });

        Thread thread = new Thread(wrapped);
        thread.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertEquals(parent, backgroundContext.get());
    }

    @Test
    @DisplayName("Async propagation with wrap(Callable)")
    void testWrapCallable() throws Exception {
        TraceContext parent = TraceContext.create();
        TraceIdPropagator.open(parent);

        Callable<String> callable = TraceIdPropagator.wrap(() ->
                TraceIdPropagator.current().map(TraceContext::traceId).orElse(null)
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(callable);
            assertEquals(parent.traceId(), future.get(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Async propagation with wrapSupplier and wrapConsumer")
    void testWrapSupplierAndConsumer() {
        TraceContext parent = TraceContext.create();
        TraceIdPropagator.open(parent);

        var supplier = TraceIdPropagator.wrapSupplier(() -> TraceIdPropagator.current().orElse(null));
        assertEquals(parent, supplier.get());

        AtomicReference<TraceContext> received = new AtomicReference<>();
        var consumer = TraceIdPropagator.wrapConsumer((String s) -> received.set(TraceIdPropagator.current().orElse(null)));
        consumer.accept("hello");
        assertEquals(parent, received.get());
    }
}
