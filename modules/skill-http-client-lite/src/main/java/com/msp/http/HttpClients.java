package com.msp.http;

import java.time.Duration;

/**
 * Static factory and utility facade for LiteHttpClient.
 */
public final class HttpClients {

    private static final LiteHttpClient DEFAULT_CLIENT = LiteHttpClient.create();

    private HttpClients() {
    }

    public static LiteHttpClient defaultClient() {
        return DEFAULT_CLIENT;
    }

    public static LiteHttpClient newClient() {
        return LiteHttpClient.create();
    }

    public static LiteHttpClient.Builder builder() {
        return LiteHttpClient.builder();
    }

    public static LiteHttpRequest get(String url) {
        return DEFAULT_CLIENT.get(url);
    }

    public static LiteHttpRequest post(String url) {
        return DEFAULT_CLIENT.post(url);
    }

    public static LiteHttpRequest put(String url) {
        return DEFAULT_CLIENT.put(url);
    }

    public static LiteHttpRequest delete(String url) {
        return DEFAULT_CLIENT.delete(url);
    }

    public static LiteHttpRequest patch(String url) {
        return DEFAULT_CLIENT.patch(url);
    }
}
