package com.msp.cose;

import java.util.*;

/**
 * Representation of CBOR (RFC 8949) data items.
 */
public sealed interface CborValue permits
        CborValue.IntegerVal,
        CborValue.ByteStringVal,
        CborValue.TextStringVal,
        CborValue.ArrayVal,
        CborValue.MapVal,
        CborValue.BooleanVal,
        CborValue.NullVal,
        CborValue.TagVal {

    record IntegerVal(long value) implements CborValue {
        @Override
        public String toString() {
            return Long.toString(value);
        }
    }

    record ByteStringVal(byte[] bytes) implements CborValue {
        public ByteStringVal {
            bytes = bytes != null ? bytes.clone() : new byte[0];
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ByteStringVal that)) return false;
            return Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("h'");
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            sb.append("'");
            return sb.toString();
        }
    }

    record TextStringVal(String text) implements CborValue {
        @Override
        public String toString() {
            return "\"" + text + "\"";
        }
    }

    record ArrayVal(List<CborValue> items) implements CborValue {
        public ArrayVal {
            items = items != null ? List.copyOf(items) : List.of();
        }
    }

    record MapVal(Map<CborValue, CborValue> entries) implements CborValue {
        public MapVal {
            entries = entries != null ? Collections.unmodifiableMap(new LinkedHashMap<>(entries)) : Map.of();
        }

        public Optional<CborValue> get(long key) {
            return Optional.ofNullable(entries.get(new IntegerVal(key)));
        }

        public Optional<CborValue> get(String key) {
            return Optional.ofNullable(entries.get(new TextStringVal(key)));
        }
    }

    record BooleanVal(boolean value) implements CborValue {
        @Override
        public String toString() {
            return Boolean.toString(value);
        }
    }

    record NullVal() implements CborValue {
        @Override
        public String toString() {
            return "null";
        }
    }

    record TagVal(long tag, CborValue value) implements CborValue {
    }
}
