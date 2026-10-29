package com.msp.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Thread-safe fluent wrapper around Java 21 java.net.http.HttpClient.
 */
public class LiteHttpClient {

    private final HttpClient rawClient;
    private final Executor executor;
    private final Map<String, List<String>> defaultHeaders;
    private final Duration defaultTimeout;
    private final int defaultMaxRetries;
    private final Duration defaultRetryDelay;

    private LiteHttpClient(Builder builder) {
        this.executor = builder.executor != null
                ? builder.executor
                : Executors.newVirtualThreadPerTaskExecutor();

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .executor(this.executor)
                .followRedirects(builder.followRedirects);

        if (builder.connectTimeout != null) {
            clientBuilder.connectTimeout(builder.connectTimeout);
        }

        this.rawClient = clientBuilder.build();
        this.defaultHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(builder.defaultHeaders));
        this.defaultTimeout = builder.defaultTimeout;
        this.defaultMaxRetries = builder.defaultMaxRetries;
        this.defaultRetryDelay = builder.defaultRetryDelay;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LiteHttpClient create() {
        return builder().build();
    }

    public LiteHttpRequest request(HttpMethod method, String url) {
        return new LiteHttpRequest(this, method, url);
    }

    public LiteHttpRequest get(String url) {
        return request(HttpMethod.GET, url);
    }

    public LiteHttpRequest post(String url) {
        return request(HttpMethod.POST, url);
    }

    public LiteHttpRequest put(String url) {
        return request(HttpMethod.PUT, url);
    }

    public LiteHttpRequest delete(String url) {
        return request(HttpMethod.DELETE, url);
    }

    public LiteHttpRequest patch(String url) {
        return request(HttpMethod.PATCH, url);
    }

    public LiteHttpRequest head(String url) {
        return request(HttpMethod.HEAD, url);
    }

    public HttpClient rawClient() {
        return rawClient;
    }

    public Executor executor() {
        return executor;
    }

    public Map<String, List<String>> defaultHeaders() {
        return defaultHeaders;
    }

    public Duration defaultTimeout() {
        return defaultTimeout;
    }

    public int defaultMaxRetries() {
        return defaultMaxRetries;
    }

    public Duration defaultRetryDelay() {
        return defaultRetryDelay;
    }

    public static class Builder {
        private Duration connectTimeout = Duration.ofSeconds(10);
        private HttpClient.Redirect followRedirects = HttpClient.Redirect.NORMAL;
        private Executor executor;
        private final Map<String, List<String>> defaultHeaders = new LinkedHashMap<>();
        private Duration defaultTimeout = Duration.ofSeconds(30);
        private int defaultMaxRetries = 0;
        private Duration defaultRetryDelay = Duration.ofMillis(200);

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder followRedirects(HttpClient.Redirect followRedirects) {
            this.followRedirects = Objects.requireNonNull(followRedirects, "followRedirects cannot be null");
            return this;
        }

        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder defaultHeader(String name, String value) {
            if (name != null && value != null) {
                defaultHeaders.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            }
            return this;
        }

        public Builder defaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
            return this;
        }

        public Builder defaultMaxRetries(int defaultMaxRetries) {
            if (defaultMaxRetries < 0) {
                throw new IllegalArgumentException("defaultMaxRetries must be >= 0");
            }
            this.defaultMaxRetries = defaultMaxRetries;
            return this;
        }

        public Builder defaultRetryDelay(Duration defaultRetryDelay) {
            this.defaultRetryDelay = Objects.requireNonNull(defaultRetryDelay, "defaultRetryDelay cannot be null");
            return this;
        }

        public LiteHttpClient build() {
            return new LiteHttpClient(this);
        }
    }
}
