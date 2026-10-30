package com.msp.starter.observability;

import com.msp.logformat.LogEvent;
import com.msp.logformat.LogLevel;
import com.msp.profiler.Profiler;
import com.msp.trace.TraceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilityKitTest {

    @Test
    @DisplayName("Integrated observability kit facade test")
    void testObservabilityKit() throws Exception {
        // Trace
        TraceContext ctx = TraceContext.create();
        try (var scope = ObservabilityKit.openTraceScope(ctx)) {
            assertEquals(ctx.traceId(), ObservabilityKit.currentTraceId());
        }

        // Exception
        Exception inner = new IllegalArgumentException("Root");
        Exception outer = new RuntimeException("Outer", inner);
        assertSame(inner, ObservabilityKit.getRootCause(outer));

        // Profiler
        Profiler p = ObservabilityKit.startProfiler("test-job");
        try (var s = p.step("sub1")) {
            Thread.sleep(1);
        }
        p.stop();
        assertNotNull(p.printTree());

        // Structured Log
        LogEvent evt = LogEvent.builder()
                .level(LogLevel.INFO)
                .logger("TestLogger")
                .message("Hello Starter")
                .build();
        String json = ObservabilityKit.formatStructuredLog(evt);
        assertTrue(json.contains("Hello Starter"));
    }
}
