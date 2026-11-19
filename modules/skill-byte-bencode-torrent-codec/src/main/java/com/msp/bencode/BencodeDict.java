package com.msp.bencode;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * Representation of a Bencode dictionary: d<key1><val1>...e.
 * Keys are ordered lexicographically by unsigned byte order.
 */
public final class BencodeDict implements BencodeValue {

    private final Map<BencodeString, BencodeValue> entries;

    public BencodeDict(Map<BencodeString, BencodeValue> map) {
        Objects.requireNonNull(map, "map cannot be null");
        Map<BencodeString, BencodeValue> sorted = new TreeMap<>();
        sorted.putAll(map);
        this.entries = Collections.unmodifiableMap(sorted);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BencodeDict empty() {
        return new BencodeDict(Map.of());
    }

    public Map<BencodeString, BencodeValue> getEntries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean containsKey(String key) {
        return entries.containsKey(BencodeString.of(key));
    }

    public Optional<BencodeValue> get(String key) {
        return Optional.ofNullable(entries.get(BencodeString.of(key)));
    }

    public Optional<BencodeValue> get(BencodeString key) {
        return Optional.ofNullable(entries.get(key));
    }

    public OptionalLong getInt(String key) {
        BencodeValue val = entries.get(BencodeString.of(key));
        if (val instanceof BencodeInteger bi) {
            return OptionalLong.of(bi.value());
        }
        return OptionalLong.empty();
    }

    public Optional<String> getString(String key) {
        BencodeValue val = entries.get(BencodeString.of(key));
        if (val instanceof BencodeString bs) {
            return Optional.of(bs.asString());
        }
        return Optional.empty();
    }

    public Optional<byte[]> getBytes(String key) {
        BencodeValue val = entries.get(BencodeString.of(key));
        if (val instanceof BencodeString bs) {
            return Optional.of(bs.getBytes());
        }
        return Optional.empty();
    }

    public Optional<BencodeList> getList(String key) {
        BencodeValue val = entries.get(BencodeString.of(key));
        if (val instanceof BencodeList bl) {
            return Optional.of(bl);
        }
        return Optional.empty();
    }

    public Optional<BencodeDict> getDict(String key) {
        BencodeValue val = entries.get(BencodeString.of(key));
        if (val instanceof BencodeDict bd) {
            return Optional.of(bd);
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BencodeDict that)) return false;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    @Override
    public String toString() {
        return entries.toString();
    }

    public static final class Builder {
        private final Map<BencodeString, BencodeValue> map = new TreeMap<>();

        public Builder put(String key, BencodeValue value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            map.put(BencodeString.of(key), value);
            return this;
        }

        public Builder put(BencodeString key, BencodeValue value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            map.put(key, value);
            return this;
        }

        public Builder put(String key, String value) {
            return put(key, BencodeString.of(value));
        }

        public Builder put(String key, byte[] bytes) {
            return put(key, BencodeString.of(bytes));
        }

        public Builder put(String key, long value) {
            return put(key, BencodeInteger.of(value));
        }

        public BencodeDict build() {
            return new BencodeDict(map);
        }
    }
}
