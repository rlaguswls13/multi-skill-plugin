package com.msp.hpack;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Represents an HTTP/2 Header Field according to RFC 7541.
 *
 * @param name      the header name (typically lowercase in HTTP/2)
 * @param value     the header value
 * @param sensitive true if this field should never be indexed (e.g. Authorization or Cookie)
 */
public record HeaderField(String name, String value, boolean sensitive) {

    public HeaderField {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }

    public HeaderField(String name, String value) {
        this(name, value, false);
    }

    /**
     * Calculates the RFC 7541 entry size (name length + value length + 32 octets).
     */
    public int size() {
        return name.getBytes(StandardCharsets.ISO_8859_1).length
                + value.getBytes(StandardCharsets.ISO_8859_1).length
                + 32;
    }

    @Override
    public String toString() {
        return name + ": " + value + (sensitive ? " [sensitive]" : "");
    }
}
