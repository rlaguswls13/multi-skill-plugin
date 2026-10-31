package com.msp.base58;

import java.util.Arrays;
import java.util.Objects;

/**
 * Result of decoding a Base58Check string with a 1-byte version prefix and payload.
 *
 * @param version 1-byte version (0-255)
 * @param payload the extracted payload bytes without version or checksum
 */
public record Base58CheckPayload(int version, byte[] payload) {

    public Base58CheckPayload {
        Objects.requireNonNull(payload, "Payload cannot be null");
        if (version < 0 || version > 255) {
            throw new IllegalArgumentException("Version must be 0-255: " + version);
        }
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Base58CheckPayload that)) return false;
        return version == that.version && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return "Base58CheckPayload[version=" + version + ", payloadLength=" + payload.length + "]";
    }
}
