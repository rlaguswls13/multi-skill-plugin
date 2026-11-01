package com.msp.signotifier;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Event delivered to registered signal listeners when an OS signal or JVM shutdown occurs.
 */
public record SignalEvent(
        SignalType type,
        Instant timestamp,
        boolean isJvmShutdown,
        Map<String, Object> metadata
) {
    public SignalEvent {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    public static SignalEvent of(SignalType type) {
        return new SignalEvent(type, Instant.now(), false, Collections.emptyMap());
    }

    public static SignalEvent of(SignalType type, boolean isJvmShutdown) {
        return new SignalEvent(type, Instant.now(), isJvmShutdown, Collections.emptyMap());
    }

    public static SignalEvent of(SignalType type, boolean isJvmShutdown, Map<String, Object> metadata) {
        return new SignalEvent(type, Instant.now(), isJvmShutdown, metadata);
    }
}
