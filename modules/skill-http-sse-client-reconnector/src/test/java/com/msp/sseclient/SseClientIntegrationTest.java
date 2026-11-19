package com.msp.sseclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SseClientIntegrationTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Should receive SSE events from HTTP server")
    void testReceiveEvents() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        List<ServerSentEvent> receivedEvents = new CopyOnWriteArrayList<>();

        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream os = exchange.getResponseBody()) {
                String payload = "id: 1\nevent: greet\ndata: Hello World\n\n" +
                                 "id: 2\ndata: Follow up\n\n";
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        });
        server.start();

        URI uri = URI.create("http://127.0.0.1:" + port + "/events");
        try (SseClient client = SseClient.builder(uri)
                .listener(new SseEventListener() {
                    @Override
                    public void onEvent(ServerSentEvent event) {
                        receivedEvents.add(event);
                        latch.countDown();
                    }
                })
                .autoReconnect(false)
                .build()) {

            client.connectAsync();

            boolean finished = latch.await(5, TimeUnit.SECONDS);
            assertTrue(finished, "Events were not received within timeout");
            assertEquals(2, receivedEvents.size());
            assertEquals("1", receivedEvents.get(0).id());
            assertEquals("greet", receivedEvents.get(0).event());
            assertEquals("Hello World", receivedEvents.get(0).data());
            assertEquals("2", receivedEvents.get(1).id());
            assertEquals("Follow up", receivedEvents.get(1).data());
        }
    }

    @Test
    @DisplayName("Should send Last-Event-ID header and reconnect after server drop")
    void testReconnectWithLastEventId() throws Exception {
        AtomicInteger connectionCount = new AtomicInteger(0);
        AtomicReference<String> receivedLastEventIdHeader = new AtomicReference<>(null);
        CountDownLatch reconnectLatch = new CountDownLatch(2);

        server.createContext("/reconnect", exchange -> {
            int count = connectionCount.incrementAndGet();
            if (count == 2) {
                String lastId = exchange.getRequestHeaders().getFirst("Last-Event-ID");
                receivedLastEventIdHeader.set(lastId);
            }

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream os = exchange.getResponseBody()) {
                if (count == 1) {
                    // Send event with ID 42 and immediately close connection
                    String payload = "id: 42\ndata: First connection payload\n\n";
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } else {
                    String payload = "id: 43\ndata: Second connection payload\n\n";
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        });
        server.start();

        URI uri = URI.create("http://127.0.0.1:" + port + "/reconnect");
        try (SseClient client = SseClient.builder(uri)
                .listener(new SseEventListener() {
                    @Override
                    public void onEvent(ServerSentEvent event) {
                        reconnectLatch.countDown();
                    }
                })
                .backoffPolicy(new BackoffPolicy(Duration.ofMillis(50), Duration.ofMillis(200), 1.5, 0.0))
                .autoReconnect(true)
                .maxReconnectAttempts(3)
                .build()) {

            client.connectAsync();

            boolean ok = reconnectLatch.await(5, TimeUnit.SECONDS);
            assertTrue(ok, "Reconnect events were not received in time");
            assertEquals(2, connectionCount.get());
            assertEquals("42", receivedLastEventIdHeader.get(), "Server should have received Last-Event-ID: 42 on reconnect");
            assertEquals("43", client.getLastEventId());
        }
    }
}
