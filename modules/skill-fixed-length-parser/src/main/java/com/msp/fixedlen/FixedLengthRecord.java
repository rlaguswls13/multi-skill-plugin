package com.msp.fixedlen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An unpacked fixed-length message record holding parsed field values.
 */
public final class FixedLengthRecord {

    private final FixedLengthSchema schema;
    private final Map<String, String> values;
    private final Map<String, String> rawPaddedValues;

    public FixedLengthRecord(FixedLengthSchema schema, Map<String, String> values, Map<String, String> rawPaddedValues) {
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.rawPaddedValues = Collections.unmodifiableMap(new LinkedHashMap<>(rawPaddedValues));
    }

    public FixedLengthSchema getSchema() {
        return schema;
    }

    public String getString(String fieldName) {
        String val = values.get(fieldName);
        if (val == null) {
            throw new FixedLengthException("Field not found in record: " + fieldName);
        }
        return val;
    }

    public Optional<String> findString(String fieldName) {
        return Optional.ofNullable(values.get(fieldName));
    }

    public int getInt(String fieldName) {
        String val = getString(fieldName);
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new FixedLengthException("Failed to parse field '" + fieldName + "' as integer: '" + val + "'", e);
        }
    }

    public long getLong(String fieldName) {
        String val = getString(fieldName);
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            throw new FixedLengthException("Failed to parse field '" + fieldName + "' as long: '" + val + "'", e);
        }
    }

    public double getDouble(String fieldName) {
        String val = getString(fieldName);
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            throw new FixedLengthException("Failed to parse field '" + fieldName + "' as double: '" + val + "'", e);
        }
    }

    public String getRaw(String fieldName) {
        String raw = rawPaddedValues.get(fieldName);
        if (raw == null) {
            throw new FixedLengthException("Field not found in record: " + fieldName);
        }
        return raw;
    }

    public boolean containsField(String fieldName) {
        return values.containsKey(fieldName);
    }

    public Map<String, String> toMap() {
        return values;
    }

    public Map<String, String> toRawMap() {
        return rawPaddedValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixedLengthRecord that)) return false;
        return Objects.equals(schema, that.schema) && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, values);
    }

    @Override
    public String toString() {
        return "FixedLengthRecord" + values;
    }
}
