package com.msp.sseclient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Resilient, Auto-Reconnecting Server-Sent Events (SSE) Client built on pure Java 21 {@link HttpClient}.
 * Features automatic Last-Event-ID tracking, exponential backoff with jitter, and custom headers.
 */
public class SseClient implements AutoCloseable {

    private final URI targetUri;
    private final HttpClient httpClient;
    private final Map<String, String> customHeaders;
    private final SseEventListener listener;
    private final BackoffPolicy backoffPolicy;
    private final boolean autoReconnect;
    private final int maxReconnectAttempts;
    private final ExecutorService internalExecutor;
    private final boolean ownsExecutor;
    private final boolean ownsHttpClient;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<String> lastEventId = new AtomicReference<>(null);
    private final AtomicReference<Long> retryOverrideMillis = new AtomicReference<>(null);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private Thread connectionThread;

    private SseClient(Builder builder) {
        this.targetUri = Objects.requireNonNull(builder.targetUri, "targetUri must not be null");
        this.listener = Objects.requireNonNull(builder.listener, "listener must not be null");
        this.ownsHttpClient = builder.httpClient == null;
        this.httpClient = builder.httpClient != null ? builder.httpClient : HttpClient.newHttpClient();
        this.customHeaders = Collections.unmodifiableMap(new HashMap<>(builder.headers));
        this.backoffPolicy = builder.backoffPolicy != null ? builder.backoffPolicy : BackoffPolicy.DEFAULT;
        this.autoReconnect = builder.autoReconnect;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;

        if (builder.initialLastEventId != null) {
            this.lastEventId.set(builder.initialLastEventId);
        }

        if (builder.executorService != null) {
            this.internalExecutor = builder.executorService;
            this.ownsExecutor = false;
        } else {
            this.internalExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sse-client-" + targetUri.getHost());
                t.setDaemon(true);
                return t;
            });
            this.ownsExecutor = true;
        }
    }

    public static Builder builder(URI uri) {
        return new Builder(uri);
    }

    /**
     * Initiates the SSE client connection asynchronously.
     *
     * @return CompletableFuture that completes when the initial connection loop is started
     */
    public CompletableFuture<Void> connectAsync() {
        return CompletableFuture.runAsync(this::runConnectionLoop, internalExecutor);
    }

    /**
     * Connects synchronously in the current thread (blocks until client is closed).
     */
    public void connect() {
        runConnectionLoop();
    }

    private void runConnectionLoop() {
        synchronized (this) {
            this.connectionThread = Thread.currentThread();
        }

        SseEventParser parser = new SseEventParser(new SseEventListener() {
            @Override
            public void onOpen() {
                listener.onOpen();
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                if (event.hasId()) {
                    lastEventId.set(event.id());
                }
                if (event.hasRetry()) {
                    retryOverrideMillis.set(event.retryMillis());
                }
                listener.onEvent(event);
            }

            @Override
            public void onComment(String comment) {
                listener.onComment(comment);
            }

            @Override
            public void onError(Throwable throwable) {
                listener.onError(throwable);
            }

            @Override
            public void onClose() {
                listener.onClose();
            }
        });

        if (lastEventId.get() != null) {
            parser.setLastEventId(lastEventId.get());
        }

        while (!closed.get()) {
            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(targetUri)
                        .GET()
                        .header("Accept", "text/event-stream")
                        .header("Cache-Control", "no-cache");

                customHeaders.forEach(reqBuilder::header);

                String currentLastId = lastEventId.get();
                if (currentLastId != null && !currentLastId.isBlank()) {
                    reqBuilder.header("Last-Event-ID", currentLastId);
                }

                HttpRequest request = reqBuilder.build();
                HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    reconnectAttempts.set(0);
                    connected.set(true);
                    listener.onOpen();

                    try (Stream<String> lineStream = response.body()) {
                        lineStream.forEach(line -> {
                            if (closed.get()) {
                                throw new ClientClosedException();
                            }
                            parser.processLine(line);
                        });
                    }

                    connected.set(false);
                    if (!autoReconnect || closed.get()) {
                        break;
                    }

                    int attempt = reconnectAttempts.incrementAndGet();
                    if (maxReconnectAttempts > 0 && attempt > maxReconnectAttempts) {
                        break;
                    }

                    Duration delay = backoffPolicy.calculateDelay(attempt - 1, retryOverrideMillis.get());
                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (statusCode == 204) {
                    // HTTP 204 No Content tells the client not to reconnect
                    break;
                } else {
                    throw new IOException("Unexpected HTTP status code: " + statusCode);
                }
            } catch (ClientClosedException ignored) {
                break;
            } catch (Exception ex) {
                connected.set(false);
                if (closed.get()) {
                    break;
                }

                listener.onError(ex);

                if (!autoReconnect) {
                    break;
                }

                int attempt = reconnectAttempts.incrementAndGet();
                if (maxReconnectAttempts > 0 && attempt > maxReconnectAttempts) {
                    listener.onError(new IOException("Maximum reconnect attempts reached (" + maxReconnectAttempts + ")"));
                    break;
                }

                Duration delay = backoffPolicy.calculateDelay(attempt - 1, retryOverrideMillis.get());
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } finally {
                connected.set(false);
            }
        }

        listener.onClose();
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    public String getLastEventId() {
        return lastEventId.get();
    }

    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            connected.set(false);
            synchronized (this) {
                if (connectionThread != null && connectionThread.isAlive()) {
                    connectionThread.interrupt();
                }
            }
            if (ownsExecutor) {
                internalExecutor.shutdownNow();
            }
            if (ownsHttpClient) {
                httpClient.close();
            }
        }
    }

    private static class ClientClosedException extends RuntimeException {}

    public static class Builder {
        private final URI targetUri;
        private HttpClient httpClient;
        private final Map<String, String> headers = new HashMap<>();
        private SseEventListener listener;
        private BackoffPolicy backoffPolicy = BackoffPolicy.DEFAULT;
        private boolean autoReconnect = true;
        private int maxReconnectAttempts = -1; // -1 = unlimited
        private String initialLastEventId;
        private ExecutorService executorService;

        public Builder(URI targetUri) {
            this.targetUri = Objects.requireNonNull(targetUri, "targetUri must not be null");
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public Builder listener(SseEventListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder backoffPolicy(BackoffPolicy backoffPolicy) {
            this.backoffPolicy = backoffPolicy;
            return this;
        }

        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        public Builder maxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }

        public Builder lastEventId(String initialLastEventId) {
            this.initialLastEventId = initialLastEventId;
            return this;
        }

        public Builder executor(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public SseClient build() {
            if (listener == null) {
                throw new IllegalStateException("SseEventListener must be provided");
            }
            return new SseClient(this);
        }
    }
}
