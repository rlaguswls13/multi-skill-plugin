package com.msp.dlq;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates a quarantined poison or failed message along with error diagnostics and metadata.
 *
 * @param <T> payload type
 */
public record DeadLetterMessage<T>(
        String id,
        T payload,
        String errorType,
        String errorMessage,
        String stackTrace,
        int attempts,
        Instant quarantinedAt,
        Map<String, String> metadata,
        MessageStatus status
) {
    public DeadLetterMessage {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(quarantinedAt, "quarantinedAt cannot be null");
        status = status == null ? MessageStatus.QUARANTINED : status;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        errorType = errorType == null ? "UnknownError" : errorType;
        errorMessage = errorMessage == null ? "" : errorMessage;
        stackTrace = stackTrace == null ? "" : stackTrace;
    }

    public static <T> DeadLetterMessage<T> of(T payload, Throwable cause, int attempts, Map<String, String> metadata) {
        String id = UUID.randomUUID().toString();
        String errorType = (cause != null) ? cause.getClass().getName() : "None";
        String errorMessage = (cause != null) ? String.valueOf(cause.getMessage()) : "";
        String stackTrace = "";
        if (cause != null) {
            StringWriter sw = new StringWriter();
            cause.printStackTrace(new PrintWriter(sw));
            stackTrace = sw.toString();
        }
        return new DeadLetterMessage<>(
                id,
                payload,
                errorType,
                errorMessage,
                stackTrace,
                attempts,
                Instant.now(),
                metadata,
                MessageStatus.QUARANTINED
        );
    }

    public DeadLetterMessage<T> withStatus(MessageStatus newStatus) {
        return new DeadLetterMessage<>(id, payload, errorType, errorMessage, stackTrace, attempts, quarantinedAt, metadata, newStatus);
    }

    public DeadLetterMessage<T> withAttempts(int newAttempts) {
        return new DeadLetterMessage<>(id, payload, errorType, errorMessage, stackTrace, newAttempts, quarantinedAt, metadata, status);
    }
}
