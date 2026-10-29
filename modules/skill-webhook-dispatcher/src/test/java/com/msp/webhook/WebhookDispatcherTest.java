package com.msp.webhook;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WebhookDispatcherTest {

    private static final String SECRET = "top-secret-signing-key";
    private static final WebhookSigner SIGNER = WebhookSigner.of(SECRET);
    private static HttpServer server;
    private static String baseUrl;

    private static final AtomicInteger retryAttempts = new AtomicInteger(0);
    private static final AtomicBoolean signatureVerifiedOnServer = new AtomicBoolean(false);

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/webhook/success", exchange -> {
            String topic = exchange.getRequestHeaders().getFirst("X-Webhook-Topic");
            String sig = exchange.getRequestHeaders().getFirst("X-Signature");
            String hubSig = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
            byte[] body = exchange.getRequestBody().readAllBytes();
            String payload = new String(body, StandardCharsets.UTF_8);

            boolean valid = SIGNER.verify(payload, sig) && SIGNER.verify(payload, hubSig) && "order.created".equals(topic);
            signatureVerifiedOnServer.set(valid);

            byte[] resp = "{\"received\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 401, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        server.createContext("/webhook/flaky", exchange -> {
            int attempt = retryAttempts.incrementAndGet();
            if (attempt < 3) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            } else {
                byte[] resp = "Recovered".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
        });

        server.createContext("/webhook/always-fail", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testSignerSignatureGenerationAndVerification() {
        WebhookSigner signer = WebhookSigner.of("my-key");
        String payload = "{\"test\":\"value\"}";

        String sig = signer.sign(payload);
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
        assertTrue(signer.verify(payload, sig));
        assertTrue(signer.verify(payload, "sha256=" + sig));
        assertFalse(signer.verify(payload, "tampered-signature"));
        assertFalse(signer.verify("altered-payload", sig));
    }

    @Test
    void testEventJsonSerialization() {
        WebhookEvent event = WebhookEvent.of("user.signup", "{\"userId\":42,\"name\":\"Kim\"}");
        String json = event.toJson();

        assertTrue(json.contains("\"topic\":\"user.signup\""));
        assertTrue(json.contains("\"data\":{\"userId\":42,\"name\":\"Kim\"}"));
    }

    @Test
    void testSuccessfulDeliveryWithSignature() throws Exception {
        signatureVerifiedOnServer.set(false);
        List<WebhookDelivery> successes = new CopyOnWriteArrayList<>();

        try (WebhookDispatcher dispatcher = WebhookDispatcher.builder()
                .secret(SECRET)
                .addListener(new WebhookListener() {
                    @Override
                    public void onSuccess(WebhookDelivery delivery) {
                        successes.add(delivery);
                    }
                })
                .build()) {

            WebhookEvent event = WebhookEvent.of("order.created", "{\"orderId\":\"ORD-1234\"}");
            CompletableFuture<WebhookDelivery> future = dispatcher.dispatch(baseUrl + "/webhook/success", event);

            WebhookDelivery delivery = future.get();
            assertTrue(delivery.isSuccess());
            assertEquals(200, delivery.statusCode());
            assertEquals(1, delivery.attempt());
            assertTrue(signatureVerifiedOnServer.get(), "Server must verify HMAC signature successfully");
            assertEquals(1, successes.size());
        }
    }

    @Test
    void testRetryOnServerError() throws Exception {
        retryAttempts.set(0);
        List<Integer> failedAttempts = new CopyOnWriteArrayList<>();

        try (WebhookDispatcher dispatcher = WebhookDispatcher.builder()
                .retryPolicy(RetryPolicy.of(3, Duration.ofMillis(30), 1.5, Duration.ofMillis(200)))
                .addListener(new WebhookListener() {
                    @Override
                    public void onFailure(WebhookDelivery delivery, Throwable cause, int attempt) {
                        failedAttempts.add(attempt);
                    }
                })
                .build()) {

            WebhookEvent event = WebhookEvent.of("retry.test", "{\"msg\":\"flaky\"}");
            WebhookDelivery delivery = dispatcher.dispatch(baseUrl + "/webhook/flaky", event).get();

            assertTrue(delivery.isSuccess());
            assertEquals(200, delivery.statusCode());
            assertEquals(3, delivery.attempt());
            assertEquals(List.of(1, 2), failedAttempts);
            assertEquals(0, dispatcher.deadLetterCount());
        }
    }

    @Test
    void testDeadLetterQueueOnExhaustion() throws Exception {
        List<WebhookDelivery> deadLetters = new CopyOnWriteArrayList<>();

        try (WebhookDispatcher dispatcher = WebhookDispatcher.builder()
                .retryPolicy(RetryPolicy.of(2, Duration.ofMillis(20), 1.0, Duration.ofMillis(50)))
                .addListener(new WebhookListener() {
                    @Override
                    public void onDeadLetter(WebhookDelivery delivery, Throwable finalCause) {
                        deadLetters.add(delivery);
                    }
                })
                .build()) {

            WebhookEvent event = WebhookEvent.of("fatal.event", "{\"cause\":\"always-fail\"}");
            WebhookDelivery delivery = dispatcher.dispatch(baseUrl + "/webhook/always-fail", event).get();

            assertTrue(delivery.isFailed());
            assertEquals(503, delivery.statusCode());
            assertEquals(2, delivery.attempt());
            assertEquals(1, dispatcher.deadLetterCount());
            assertEquals(1, deadLetters.size());

            List<WebhookDelivery> drained = dispatcher.drainDeadLetters();
            assertEquals(1, drained.size());
            assertEquals("fatal.event", drained.getFirst().event().topic());
            assertEquals(0, dispatcher.deadLetterCount());
        }
    }
}
