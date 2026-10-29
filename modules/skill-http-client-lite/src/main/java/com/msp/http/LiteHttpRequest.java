package com.msp.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Fluent request builder for constructing and executing HTTP requests with retries, timeouts, and headers.
 */
public class LiteHttpRequest {

    private final LiteHttpClient client;
    private final HttpMethod method;
    private final String url;
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private final Map<String, List<String>> queryParams = new LinkedHashMap<>();
    private byte[] bodyBytes;
    private Duration timeout;
    private int maxRetries;
    private Duration retryDelay;
    private Predicate<LiteHttpResponse> retryPredicate;

    public LiteHttpRequest(LiteHttpClient client, HttpMethod method, String url) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.method = Objects.requireNonNull(method, "method cannot be null");
        this.url = Objects.requireNonNull(url, "url cannot be null");
        this.timeout = client.defaultTimeout();
        this.maxRetries = client.defaultMaxRetries();
        this.retryDelay = client.defaultRetryDelay();
        this.retryPredicate = LiteHttpResponse::isServerError;

        // Apply client-level default headers
        client.defaultHeaders().forEach((name, values) -> {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).addAll(values);
        });
    }

    public LiteHttpRequest header(String name, String value) {
        if (name != null && value != null) {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return this;
    }

    public LiteHttpRequest headers(Map<String, String> newHeaders) {
        if (newHeaders != null) {
            newHeaders.forEach(this::header);
        }
        return this;
    }

    public LiteHttpRequest bearerToken(String token) {
        return header("Authorization", "Bearer " + Objects.requireNonNull(token, "token cannot be null"));
    }

    public LiteHttpRequest contentType(String contentType) {
        return header("Content-Type", Objects.requireNonNull(contentType, "contentType cannot be null"));
    }

    public LiteHttpRequest queryParam(String name, Object value) {
        if (name != null && value != null) {
            queryParams.computeIfAbsent(name, k -> new ArrayList<>()).add(String.valueOf(value));
        }
        return this;
    }

    public LiteHttpRequest queryParams(Map<String, ?> params) {
        if (params != null) {
            params.forEach(this::queryParam);
        }
        return this;
    }

    public LiteHttpRequest body(String body) {
        this.bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
        return this;
    }

    public LiteHttpRequest body(byte[] body) {
        this.bodyBytes = body != null ? body.clone() : null;
        return this;
    }

    public LiteHttpRequest jsonBody(String json) {
        contentType("application/json; charset=UTF-8");
        return body(json);
    }

    public LiteHttpRequest timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public LiteHttpRequest maxRetries(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        this.maxRetries = maxRetries;
        return this;
    }

    public LiteHttpRequest retryDelay(Duration retryDelay) {
        this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay cannot be null");
        return this;
    }

    public LiteHttpRequest retryOn(Predicate<LiteHttpResponse> retryPredicate) {
        this.retryPredicate = Objects.requireNonNull(retryPredicate, "retryPredicate cannot be null");
        return this;
    }

    public HttpMethod method() {
        return method;
    }

    public String buildUrl() {
        if (queryParams.isEmpty()) {
            return url;
        }

        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = url.contains("?");
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            String encodedKey = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            for (String val : entry.getValue()) {
                sb.append(hasQuery ? '&' : '?');
                hasQuery = true;
                sb.append(encodedKey);
                sb.append('=');
                sb.append(URLEncoder.encode(val, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    public LiteHttpResponse execute() {
        String fullUrl = buildUrl();
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(fullUrl));

        headers.forEach((name, values) -> {
            for (String val : values) {
                builder.header(name, val);
            }
        });

        if (timeout != null) {
            builder.timeout(timeout);
        }

        HttpRequest.BodyPublisher publisher = (bodyBytes != null)
                ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                : HttpRequest.BodyPublishers.noBody();

        switch (method) {
            case GET -> builder.GET();
            case POST -> builder.POST(publisher);
            case PUT -> builder.PUT(publisher);
            case DELETE -> builder.DELETE();
            case PATCH -> builder.method("PATCH", publisher);
            case HEAD -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        }

        HttpRequest httpRequest = builder.build();

        int attempts = 0;
        while (true) {
            attempts++;
            try {
                HttpResponse<byte[]> response = client.rawClient().send(
                        httpRequest,
                        HttpResponse.BodyHandlers.ofByteArray()
                );

                LiteHttpResponse liteResponse = new LiteHttpResponse(
                        response.statusCode(),
                        response.headers().map(),
                        response.body()
                );

                if (attempts <= maxRetries && retryPredicate.test(liteResponse)) {
                    sleepBeforeRetry(attempts);
                    continue;
                }

                return liteResponse;
            } catch (IOException e) {
                if (attempts <= maxRetries) {
                    sleepBeforeRetry(attempts);
                    continue;
                }
                throw new HttpException("HTTP " + method + " request failed after " + attempts + " attempts to " + fullUrl, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpException("HTTP request interrupted: " + method + " " + fullUrl, e);
            }
        }
    }

    public CompletableFuture<LiteHttpResponse> executeAsync() {
        return CompletableFuture.supplyAsync(this::execute, client.executor());
    }

    private void sleepBeforeRetry(int attempt) {
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpException("Interrupted while waiting for HTTP retry attempt #" + attempt, e);
        }
    }
}
