package com.msp.sseclient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SseEventParserTest {

    @Test
    @DisplayName("Should parse standard single-line SSE event")
    void testStandardSingleLineEvent() {
        List<ServerSentEvent> events = new ArrayList<>();
        SseEventParser parser = new SseEventParser(new SseEventListener() {
            @Override
            public void onEvent(ServerSentEvent event) {
                events.add(event);
            }
        });

        parser.processLine("id: 101");
        parser.processLine("event: user-login");
        parser.processLine("data: {\"userId\": 42}");
        parser.processLine(""); // Dispatch frame

        assertEquals(1, events.size());
        ServerSentEvent event = events.get(0);
        assertEquals("101", event.id());
        assertEquals("user-login", event.event());
        assertEquals("{\"userId\": 42}", event.data());
        assertNull(event.retryMillis());
        assertEquals("101", parser.getLastEventId());
    }

    @Test
    @DisplayName("Should parse multi-line data joined with newline")
    void testMultiLineDataEvent() {
        List<ServerSentEvent> events = new ArrayList<>();
        SseEventParser parser = new SseEventParser(events::add);

        parser.processLine("data: Line 1");
        parser.processLine("data: Line 2");
        parser.processLine("data: Line 3");
        parser.processLine(""); // Dispatch

        assertEquals(1, events.size());
        ServerSentEvent event = events.get(0);
        assertEquals("message", event.event());
        assertEquals("Line 1\nLine 2\nLine 3", event.data());
    }

    @Test
    @DisplayName("Should parse retry directive and comments")
    void testRetryAndComments() {
        List<ServerSentEvent> events = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        SseEventParser parser = new SseEventParser(new SseEventListener() {
            @Override
            public void onEvent(ServerSentEvent event) {
                events.add(event);
            }

            @Override
            public void onComment(String comment) {
                comments.add(comment);
            }
        });

        parser.processLine(": keep-alive ping");
        parser.processLine("retry: 5000");
        parser.processLine("data: ping-ack");
        parser.processLine("");

        assertEquals(1, comments.size());
        assertEquals("keep-alive ping", comments.get(0));

        assertEquals(1, events.size());
        assertEquals(5000L, events.get(0).retryMillis());
        assertEquals("ping-ack", events.get(0).data());
    }

    @Test
    @DisplayName("Should correctly handle leading spaces after colon per spec")
    void testLeadingSpaceHandling() {
        List<ServerSentEvent> events = new ArrayList<>();
        SseEventParser parser = new SseEventParser(events::add);

        // One space after colon -> stripped. Two spaces -> one stripped, one preserved.
        parser.processLine("data:  indented text");
        parser.processLine("");

        assertEquals(1, events.size());
        assertEquals(" indented text", events.get(0).data());
    }

    @Test
    @DisplayName("Should preserve lastEventId across subsequent events without id")
    void testLastEventIdPersistence() {
        List<ServerSentEvent> events = new ArrayList<>();
        SseEventParser parser = new SseEventParser(events::add);

        parser.processLine("id: evt-1");
        parser.processLine("data: first");
        parser.processLine("");

        parser.processLine("data: second");
        parser.processLine("");

        assertEquals(2, events.size());
        assertEquals("evt-1", events.get(0).id());
        assertNull(events.get(1).id());
        assertEquals("evt-1", parser.getLastEventId());
    }
}
