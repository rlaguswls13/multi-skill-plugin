package com.msp.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SseEventTest {

    @Test
    @DisplayName("Format full SSE event according to W3C specification")
    void testFormatFullEvent() {
        SseEvent event = SseEvent.builder()
                .id("ev-101")
                .event("user-login")
                .data("{\"userId\": 1234}")
                .retry(5000L)
                .comment("heartbeat")
                .build();

        String wire = event.format();
        assertTrue(wire.contains(": heartbeat\n"));
        assertTrue(wire.contains("id: ev-101\n"));
        assertTrue(wire.contains("event: user-login\n"));
        assertTrue(wire.contains("retry: 5000\n"));
        assertTrue(wire.contains("data: {\"userId\": 1234}\n"));
        assertTrue(wire.endsWith("\n\n"));
    }

    @Test
    @DisplayName("Format multiline data SSE event")
    void testMultilineData() {
        SseEvent event = SseEvent.dataOnly("line1\nline2");
        String wire = event.format();
        assertEquals("data: line1\ndata: line2\n\n", wire);
    }

    @Test
    @DisplayName("SseBroadcaster dispatch and unsubscribe test")
    void testBroadcaster() throws Exception {
        SseBroadcaster broadcaster = new SseBroadcaster();
        List<String> received = new ArrayList<>();

        AutoCloseable sub = broadcaster.subscribe(received::add);
        assertEquals(1, broadcaster.subscriberCount());

        broadcaster.broadcast(SseEvent.of("alert", "Server warming"));
        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("event: alert\n"));

        broadcaster.ping();
        assertEquals(2, received.size());
        assertTrue(received.get(1).contains(": keepalive\n"));

        sub.close();
        assertEquals(0, broadcaster.subscriberCount());
    }
}
