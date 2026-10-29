package com.msp.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LiteHttpClientTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final AtomicInteger retryAttempts = new AtomicInteger(0);

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/get", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String customHeader = exchange.getRequestHeaders().getFirst("X-Custom");
            String response = "query=" + query + ";custom=" + customHeader;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/post-json", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("application/json; charset=UTF-8", exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.createContext("/put", exchange -> {
            assertEquals("PUT", exchange.getRequestMethod());
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.createContext("/delete", exchange -> {
            assertEquals("DELETE", exchange.getRequestMethod());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        server.createContext("/patch", exchange -> {
            assertEquals("PATCH", exchange.getRequestMethod());
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.createContext("/auth", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if ("Bearer secret123".equals(auth)) {
                byte[] resp = "Authorized".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            } else {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
            }
        });

        server.createContext("/retry-flaky", exchange -> {
            int attempt = retryAttempts.incrementAndGet();
            if (attempt < 3) {
                byte[] err = "Service Unavailable".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, err.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(err);
                }
            } else {
                byte[] ok = "Recovered".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, ok.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(ok);
                }
            }
        });

        server.createContext("/always-500", exchange -> {
            byte[] err = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        });

        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
            }
            byte[] ok = "Finally".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
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
    void testGetWithQueryParamsAndHeaders() {
        LiteHttpClient client = HttpClients.newClient();
        LiteHttpResponse response = client.get(baseUrl + "/get")
                .queryParam("name", "Alice")
                .queryParam("order", "asc")
                .header("X-Custom", "HelloVal")
                .execute();

        assertTrue(response.isSuccess());
        assertEquals(200, response.statusCode());
        assertEquals("query=name=Alice&order=asc;custom=HelloVal", response.body());
        assertTrue(response.header("Content-Type").isPresent());
    }

    @Test
    void testPostJsonPayload() {
        LiteHttpClient client = HttpClients.newClient();
        String jsonInput = "{\"title\":\"Item 1\",\"price\":99}";

        LiteHttpResponse response = client.post(baseUrl + "/post-json")
                .jsonBody(jsonInput)
                .execute();

        assertEquals(201, response.statusCode());
        assertTrue(response.isSuccess());
        assertEquals(jsonInput, response.body());
    }

    @Test
    void testPutDeleteAndPatch() {
        LiteHttpClient client = HttpClients.newClient();

        LiteHttpResponse putResp = client.put(baseUrl + "/put")
                .body("updated-data")
                .execute();
        assertEquals(200, putResp.statusCode());
        assertEquals("updated-data", putResp.body());

        LiteHttpResponse patchResp = client.patch(baseUrl + "/patch")
                .body("patch-data")
                .execute();
        assertEquals(200, patchResp.statusCode());
        assertEquals("patch-data", patchResp.body());

        LiteHttpResponse deleteResp = client.delete(baseUrl + "/delete").execute();
        assertEquals(204, deleteResp.statusCode());
        assertTrue(deleteResp.isSuccess());
    }

    @Test
    void testBearerAuthentication() {
        LiteHttpClient client = HttpClients.newClient();

        LiteHttpResponse successResp = client.get(baseUrl + "/auth")
                .bearerToken("secret123")
                .execute();
        assertEquals(200, successResp.statusCode());
        assertEquals("Authorized", successResp.body());

        LiteHttpResponse failResp = client.get(baseUrl + "/auth")
                .bearerToken("wrong-token")
                .execute();
        assertEquals(401, failResp.statusCode());
        assertTrue(failResp.isClientError());
    }

    @Test
    void testAutomaticRetryOn503() {
        retryAttempts.set(0);
        LiteHttpClient client = LiteHttpClient.builder()
                .defaultMaxRetries(3)
                .defaultRetryDelay(Duration.ofMillis(50))
                .build();

        LiteHttpResponse response = client.get(baseUrl + "/retry-flaky").execute();

        assertEquals(200, response.statusCode());
        assertEquals("Recovered", response.body());
        assertEquals(3, retryAttempts.get());
    }

    @Test
    void testRetryExhaustedReturns500() {
        LiteHttpClient client = LiteHttpClient.builder()
                .defaultMaxRetries(2)
                .defaultRetryDelay(Duration.ofMillis(20))
                .build();

        LiteHttpResponse response = client.get(baseUrl + "/always-500").execute();

        assertEquals(500, response.statusCode());
        assertTrue(response.isServerError());
    }

    @Test
    void testRequestTimeoutThrowsHttpException() {
        LiteHttpClient client = HttpClients.newClient();

        assertThrows(HttpException.class, () -> {
            client.get(baseUrl + "/slow")
                    .timeout(Duration.ofMillis(150))
                    .maxRetries(0)
                    .execute();
        });
    }

    @Test
    void testAsyncExecution() throws Exception {
        CompletableFuture<LiteHttpResponse> future = HttpClients.get(baseUrl + "/get")
                .queryParam("mode", "async")
                .executeAsync();

        LiteHttpResponse response = future.get();
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("mode=async"));
    }

    @Test
    void testDefaultHeadersConfiguration() {
        LiteHttpClient client = LiteHttpClient.builder()
                .defaultHeader("X-Custom", "DefaultVal")
                .build();

        LiteHttpResponse response = client.get(baseUrl + "/get").execute();
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("custom=DefaultVal"));
    }
}
