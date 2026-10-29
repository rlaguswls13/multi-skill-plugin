package com.msp.webhook;

import java.time.Instant;
import java.util.Objects;

/**
 * Outcome and metadata of a webhook delivery attempt.
 */
public record WebhookDelivery(
        WebhookEvent event,
        String targetUrl,
        int attempt,
        int statusCode,
        Throwable failureCause,
        Instant deliveredAt
) {

    public WebhookDelivery {
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(targetUrl, "targetUrl cannot be null");
        deliveredAt = deliveredAt != null ? deliveredAt : Instant.now();
    }

    public static WebhookDelivery success(WebhookEvent event, String targetUrl, int attempt, int statusCode) {
        return new WebhookDelivery(event, targetUrl, attempt, statusCode, null, Instant.now());
    }

    public static WebhookDelivery failure(WebhookEvent event, String targetUrl, int attempt, int statusCode, Throwable cause) {
        return new WebhookDelivery(event, targetUrl, attempt, statusCode, cause, Instant.now());
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isFailed() {
        return !isSuccess();
    }
}
