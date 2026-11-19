package com.msp.ocsp;

import java.util.Arrays;
import java.util.Objects;

/**
 * RFC 6960 §4.2.1 {@code ResponderID} CHOICE ({@code byName [1]} or {@code byKey [2]}).
 */
public sealed interface ResponderId permits ResponderId.ByName, ResponderId.ByKey {

    record ByName(byte[] encodedName) implements ResponderId {
        public ByName {
            Objects.requireNonNull(encodedName, "encodedName must not be null");
            encodedName = encodedName.clone();
        }

        @Override
        public byte[] encodedName() {
            return encodedName.clone();
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof ByName other && Arrays.equals(encodedName, other.encodedName));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(encodedName);
        }
    }

    record ByKey(byte[] keyHash) implements ResponderId {
        public ByKey {
            Objects.requireNonNull(keyHash, "keyHash must not be null");
            keyHash = keyHash.clone();
        }

        @Override
        public byte[] keyHash() {
            return keyHash.clone();
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof ByKey other && Arrays.equals(keyHash, other.keyHash));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(keyHash);
        }
    }
}
