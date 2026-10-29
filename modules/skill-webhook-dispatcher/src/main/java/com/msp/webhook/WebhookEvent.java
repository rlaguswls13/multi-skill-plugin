package com.msp.webhook;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable webhook event payload containing an identifier, topic, timestamp, and payload data.
 */
public record WebhookEvent(
        String id,
        String topic,
        Instant timestamp,
        String data
) {

    public WebhookEvent {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(topic, "topic cannot be null");
        timestamp = timestamp != null ? timestamp : Instant.now();
        data = data != null ? data : "";
    }

    public static WebhookEvent of(String topic, String data) {
        return new WebhookEvent(UUID.randomUUID().toString(), topic, Instant.now(), data);
    }

    public static WebhookEvent of(String id, String topic, String data) {
        return new WebhookEvent(id, topic, Instant.now(), data);
    }

    /**
     * Serializes this event into standard JSON format without external JSON library dependencies.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(128 + (data != null ? data.length() : 0));
        sb.append("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"topic\":\"").append(escapeJson(topic)).append("\",");
        sb.append("\"timestamp\":\"").append(timestamp.toString()).append("\",");

        String trimmed = (data != null) ? data.trim() : "";
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // Already raw JSON structure
            sb.append("\"data\":").append(trimmed);
        } else {
            sb.append("\"data\":\"").append(escapeJson(trimmed)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
