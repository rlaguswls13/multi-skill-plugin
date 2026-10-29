package com.msp.webhook;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous webhook dispatcher providing HMAC-SHA256 signing, configurable exponential backoff retries,
 * delivery listeners, and a Dead-Letter Queue (DLQ).
 */
public class WebhookDispatcher implements AutoCloseable {

    private final HttpClient httpClient;
    private final WebhookSigner signer;
    private final RetryPolicy retryPolicy;
    private final Duration requestTimeout;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final List<WebhookListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<WebhookDelivery> deadLetterQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private WebhookDispatcher(Builder builder) {
        this.signer = builder.signer;
        this.retryPolicy = builder.retryPolicy != null ? builder.retryPolicy : RetryPolicy.defaultPolicy();
        this.requestTimeout = builder.requestTimeout != null ? builder.requestTimeout : Duration.ofSeconds(10);
        this.listeners.addAll(builder.listeners);

        if (builder.executor != null) {
            this.executor = builder.executor;
            this.ownsExecutor = false;
        } else {
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            this.ownsExecutor = true;
        }

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .executor(this.executor);
        if (builder.connectTimeout != null) {
            clientBuilder.connectTimeout(builder.connectTimeout);
        }
        this.httpClient = clientBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WebhookDispatcher create() {
        return builder().build();
    }

    public CompletableFuture<WebhookDelivery> dispatch(String targetUrl, WebhookEvent event) {
        if (!running.get()) {
            throw new IllegalStateException("WebhookDispatcher has been shut down");
        }
        Objects.requireNonNull(targetUrl, "targetUrl cannot be null");
        Objects.requireNonNull(event, "event cannot be null");

        return CompletableFuture.supplyAsync(() -> executeDelivery(targetUrl, event), executor);
    }

    public CompletableFuture<WebhookDelivery> enqueue(String targetUrl, WebhookEvent event) {
        return dispatch(targetUrl, event);
    }

    private WebhookDelivery executeDelivery(String targetUrl, WebhookEvent event) {
        String payload = event.toJson();
        int maxAttempts = retryPolicy.maxAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                Duration backoff = retryPolicy.delayForAttempt(attempt);
                if (!backoff.isZero() && !backoff.isNegative()) {
                    try {
                        Thread.sleep(backoff.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        WebhookDelivery delivery = WebhookDelivery.failure(
                                event, targetUrl, attempt, -1,
                                new IllegalStateException("Webhook delivery thread interrupted", e)
                        );
                        routeToDeadLetter(delivery, delivery.failureCause());
                        return delivery;
                    }
                }
            }

            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .timeout(requestTimeout)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("X-Webhook-Id", event.id())
                        .header("X-Webhook-Topic", event.topic())
                        .header("X-Webhook-Timestamp", event.timestamp().toString())
                        .POST(HttpRequest.BodyPublishers.ofString(payload));

                if (signer != null) {
                    reqBuilder.header("X-Signature", signer.sign(payload));
                    reqBuilder.header("X-Hub-Signature-256", signer.signHub(payload));
                }

                HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();

                if (statusCode >= 200 && statusCode < 300) {
                    WebhookDelivery delivery = WebhookDelivery.success(event, targetUrl, attempt, statusCode);
                    notifySuccess(delivery);
                    return delivery;
                } else {
                    Throwable cause = new IllegalStateException("HTTP " + statusCode + ": " + response.body());
                    WebhookDelivery delivery = WebhookDelivery.failure(event, targetUrl, attempt, statusCode, cause);
                    notifyFailure(delivery, cause, attempt);

                    if (attempt == maxAttempts) {
                        routeToDeadLetter(delivery, cause);
                        return delivery;
                    }
                }
            } catch (IOException e) {
                WebhookDelivery delivery = WebhookDelivery.failure(event, targetUrl, attempt, -1, e);
                notifyFailure(delivery, e, attempt);

                if (attempt == maxAttempts) {
                    routeToDeadLetter(delivery, e);
                    return delivery;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                WebhookDelivery delivery = WebhookDelivery.failure(event, targetUrl, attempt, -1, e);
                routeToDeadLetter(delivery, e);
                return delivery;
            }
        }

        // Fallback (should not be reached)
        WebhookDelivery delivery = WebhookDelivery.failure(
                event, targetUrl, maxAttempts, -1,
                new IllegalStateException("Max retry attempts reached without response")
        );
        routeToDeadLetter(delivery, delivery.failureCause());
        return delivery;
    }

    private void routeToDeadLetter(WebhookDelivery delivery, Throwable finalCause) {
        deadLetterQueue.offer(delivery);
        notifyDeadLetter(delivery, finalCause);
    }

    private void notifySuccess(WebhookDelivery delivery) {
        for (WebhookListener listener : listeners) {
            try {
                listener.onSuccess(delivery);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyFailure(WebhookDelivery delivery, Throwable cause, int attempt) {
        for (WebhookListener listener : listeners) {
            try {
                listener.onFailure(delivery, cause, attempt);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyDeadLetter(WebhookDelivery delivery, Throwable finalCause) {
        for (WebhookListener listener : listeners) {
            try {
                listener.onDeadLetter(delivery, finalCause);
            } catch (Exception ignored) {
            }
        }
    }

    public void addListener(WebhookListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(WebhookListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public List<WebhookDelivery> deadLetterQueue() {
        return List.copyOf(deadLetterQueue);
    }

    public List<WebhookDelivery> drainDeadLetters() {
        List<WebhookDelivery> drained = new ArrayList<>();
        WebhookDelivery item;
        while ((item = deadLetterQueue.poll()) != null) {
            drained.add(item);
        }
        return Collections.unmodifiableList(drained);
    }

    public int deadLetterCount() {
        return deadLetterQueue.size();
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            if (ownsExecutor) {
                executor.shutdown();
            }
        }
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        if (ownsExecutor) {
            return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        return true;
    }

    @Override
    public void close() {
        shutdown();
    }

    public static class Builder {
        private WebhookSigner signer;
        private RetryPolicy retryPolicy = RetryPolicy.defaultPolicy();
        private Duration requestTimeout = Duration.ofSeconds(10);
        private Duration connectTimeout = Duration.ofSeconds(5);
        private ExecutorService executor;
        private final List<WebhookListener> listeners = new ArrayList<>();

        public Builder signer(WebhookSigner signer) {
            this.signer = signer;
            return this;
        }

        public Builder secret(String secret) {
            this.signer = secret != null ? WebhookSigner.of(secret) : null;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder addListener(WebhookListener listener) {
            if (listener != null) {
                this.listeners.add(listener);
            }
            return this;
        }

        public WebhookDispatcher build() {
            return new WebhookDispatcher(this);
        }
    }
}
