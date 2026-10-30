package com.msp.cursor;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Encapsulates an opaque cursor token consisting of an entity unique ID,
 * sort order value, and generation timestamp.
 *
 * @param id        Unique primary key / identifier of the entity
 * @param sortValue Value of the sort column (e.g. timestamp, score, sequential code)
 * @param timestamp Generation timestamp of this cursor token
 */
public record CursorToken(
        String id,
        String sortValue,
        Instant timestamp
) implements Serializable {

    public CursorToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sortValue, "sortValue must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static CursorToken of(String id, String sortValue) {
        return new CursorToken(id, sortValue, Instant.now());
    }

    public static CursorToken of(String id, String sortValue, Instant timestamp) {
        return new CursorToken(id, sortValue, timestamp);
    }

    /**
     * Encodes this cursor token into a URL-safe Base64 string without padding.
     */
    public String encode() {
        long epochMilli = timestamp.toEpochMilli();
        int idLength = id.length();
        // Robust length-prefixed protocol: <epochMilli>:<idLen>:<id><sortValue>
        String payload = epochMilli + ":" + idLength + ":" + id + sortValue;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a URL-safe Base64 string into a {@link CursorToken}.
     *
     * @param cursor URL-safe cursor string
     * @return decoded CursorToken
     * @throws IllegalArgumentException if the cursor string is invalid or corrupted
     */
    public static CursorToken decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new IllegalArgumentException("Cursor string must not be null or blank");
        }

        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor.trim());
            String payload = new String(bytes, StandardCharsets.UTF_8);

            int firstColon = payload.indexOf(':');
            if (firstColon == -1) {
                throw new IllegalArgumentException("Invalid cursor format: missing timestamp delimiter");
            }

            int secondColon = payload.indexOf(':', firstColon + 1);
            if (secondColon == -1) {
                throw new IllegalArgumentException("Invalid cursor format: missing id length delimiter");
            }

            long epochMilli = Long.parseLong(payload.substring(0, firstColon));
            int idLength = Integer.parseInt(payload.substring(firstColon + 1, secondColon));

            int idStart = secondColon + 1;
            int idEnd = idStart + idLength;
            if (idEnd > payload.length()) {
                throw new IllegalArgumentException("Corrupted cursor payload: id length exceeds content");
            }

            String id = payload.substring(idStart, idEnd);
            String sortValue = payload.substring(idEnd);

            return new CursorToken(id, sortValue, Instant.ofEpochMilli(epochMilli));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to decode cursor token: '" + cursor + "'", e);
        }
    }
}
