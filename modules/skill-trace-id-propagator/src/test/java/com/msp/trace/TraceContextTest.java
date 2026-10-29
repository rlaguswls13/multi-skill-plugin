package com.msp.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextTest {

    @Test
    @DisplayName("Create TraceContext with defaults and check getters")
    void testCreateDefault() {
        TraceContext ctx = TraceContext.create();
        assertNotNull(ctx.traceId());
        assertEquals(32, ctx.traceId().length());
        assertNotNull(ctx.spanId());
        assertEquals(16, ctx.spanId().length());
        assertNull(ctx.parentSpanId());
        assertTrue(ctx.isSampled());
        assertEquals((byte) 0x01, ctx.flags());
    }

    @Test
    @DisplayName("Create unsampled TraceContext")
    void testCreateUnsampled() {
        TraceContext ctx = TraceContext.create(false);
        assertFalse(ctx.isSampled());
        assertEquals((byte) 0x00, ctx.flags());
    }

    @Test
    @DisplayName("Child span inherits traceId and flags with updated parentSpanId")
    void testChildSpan() {
        TraceContext parent = TraceContext.create();
        TraceContext child = parent.childSpan();

        assertEquals(parent.traceId(), child.traceId());
        assertNotEquals(parent.spanId(), child.spanId());
        assertEquals(parent.spanId(), child.parentSpanId());
        assertEquals(parent.flags(), child.flags());
        assertEquals(parent.isSampled(), child.isSampled());

        TraceContext unsampledChild = parent.childSpan(false);
        assertEquals(parent.traceId(), unsampledChild.traceId());
        assertFalse(unsampledChild.isSampled());
    }

    @Test
    @DisplayName("W3C traceparent serialization and deserialization roundtrip")
    void testW3cTraceparentRoundTrip() {
        String tId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String sId = "00f067aa0ba902b7";
        TraceContext ctx = TraceContext.of(tId, sId);

        String traceparent = ctx.toTraceParent();
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", traceparent);

        TraceContext parsed = TraceContext.fromTraceParent(traceparent);
        assertEquals(tId, parsed.traceId());
        assertEquals(sId, parsed.spanId());
        assertNull(parsed.parentSpanId());
        assertTrue(parsed.isSampled());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "invalid",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7",
            "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
            "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            "00-4bf92f3577b34da6a3ce929d0e0e47zz-00f067aa0ba902b7-01"
    })
    @DisplayName("Invalid W3C traceparent headers throw IllegalArgumentException")
    void testInvalidW3cTraceparent(String invalidHeader) {
        assertThrows(IllegalArgumentException.class, () -> TraceContext.fromTraceParent(invalidHeader));
    }

    @Test
    @DisplayName("B3 single header serialization and deserialization")
    void testB3SingleHeader() {
        TraceContext ctx = TraceContext.of("463ac35c9f6413ad48485a3953bb6124", "6a846a6552a03633", "0000000000000001", true);
        String b3 = ctx.toB3SingleHeader();
        assertEquals("463ac35c9f6413ad48485a3953bb6124-6a846a6552a03633-1-0000000000000001", b3);

        TraceContext parsed = TraceContext.fromB3SingleHeader(b3);
        assertEquals(ctx.traceId(), parsed.traceId());
        assertEquals(ctx.spanId(), parsed.spanId());
        assertEquals(ctx.parentSpanId(), parsed.parentSpanId());
        assertTrue(parsed.isSampled());

        TraceContext noParent = TraceContext.of("463ac35c9f6413ad48485a3953bb6124", "6a846a6552a03633");
        assertEquals("463ac35c9f6413ad48485a3953bb6124-6a846a6552a03633-1", noParent.toB3SingleHeader());
    }

    @Test
    @DisplayName("B3 16-hex short trace ID padding")
    void testB3ShortTraceId() {
        String shortB3 = "463ac35c9f6413ad-6a846a6552a03633-1";
        TraceContext parsed = TraceContext.fromB3SingleHeader(shortB3);
        assertEquals("0000000000000000463ac35c9f6413ad", parsed.traceId());
        assertEquals("6a846a6552a03633", parsed.spanId());
    }

    @Test
    @DisplayName("B3 multi-headers export and import with case insensitivity")
    void testB3MultiHeaders() {
        TraceContext ctx = TraceContext.of("463ac35c9f6413ad48485a3953bb6124", "6a846a6552a03633", "1111222233334444", true);
        Map<String, String> headers = ctx.toB3Headers();

        assertEquals("463ac35c9f6413ad48485a3953bb6124", headers.get(TraceContext.B3_TRACE_ID));
        assertEquals("6a846a6552a03633", headers.get(TraceContext.B3_SPAN_ID));
        assertEquals("1", headers.get(TraceContext.B3_SAMPLED));
        assertEquals("1111222233334444", headers.get(TraceContext.B3_PARENT_SPAN_ID));

        Map<String, String> lowercaseHeaders = Map.of(
                "x-b3-traceid", "463ac35c9f6413ad48485a3953bb6124",
                "x-b3-spanid", "6a846a6552a03633",
                "x-b3-sampled", "0"
        );

        TraceContext parsed = TraceContext.fromB3Headers(lowercaseHeaders);
        assertEquals("463ac35c9f6413ad48485a3953bb6124", parsed.traceId());
        assertEquals("6a846a6552a03633", parsed.spanId());
        assertFalse(parsed.isSampled());
        assertNull(parsed.parentSpanId());
    }
}
