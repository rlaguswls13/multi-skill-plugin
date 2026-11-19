package com.msp.paseto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Decrypted and authenticated PASETO v3.local token payload and optional footer.
 */
public final class PasetoV3Token {

    private final byte[] payload;
    private final byte[] footer;

    public PasetoV3Token(byte[] payload, byte[] footer) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(footer, "footer must not be null");
        this.payload = payload.clone();
        this.footer = footer.clone();
    }

    /**
     * Returns a defensive copy of the decrypted payload bytes.
     */
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Returns the decrypted payload decoded as UTF-8.
     */
    public String payloadAsUtf8() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    /**
     * Returns a defensive copy of the authenticated footer bytes (empty array if no footer).
     */
    public byte[] footer() {
        return footer.clone();
    }

    /**
     * Returns the authenticated footer decoded as UTF-8 (empty string if no footer).
     */
    public String footerAsUtf8() {
        return new String(footer, StandardCharsets.UTF_8);
    }

    public boolean hasFooter() {
        return footer.length > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PasetoV3Token that)) return false;
        return Arrays.equals(payload, that.payload) && Arrays.equals(footer, that.footer);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(payload) + Arrays.hashCode(footer);
    }
}
