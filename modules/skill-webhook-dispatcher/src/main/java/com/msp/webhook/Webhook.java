package com.msp.webhook;

/**
 * Static factory facade for Webhook operations, signers, and dispatchers.
 */
public final class Webhook {

    private Webhook() {
    }

    public static WebhookEvent event(String topic, String data) {
        return WebhookEvent.of(topic, data);
    }

    public static WebhookEvent event(String id, String topic, String data) {
        return WebhookEvent.of(id, topic, data);
    }

    public static WebhookSigner signer(String secret) {
        return WebhookSigner.of(secret);
    }

    public static WebhookSigner signer(byte[] secretBytes) {
        return WebhookSigner.of(secretBytes);
    }

    public static WebhookDispatcher.Builder dispatcherBuilder() {
        return WebhookDispatcher.builder();
    }

    public static WebhookDispatcher dispatcher() {
        return WebhookDispatcher.create();
    }
}
