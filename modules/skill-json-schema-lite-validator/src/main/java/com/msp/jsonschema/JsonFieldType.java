package com.msp.jsonschema;

import java.util.Collection;
import java.util.Map;

/**
 * Supported JSON field types for schema validation.
 */
public enum JsonFieldType {
    STRING,
    NUMBER,
    BOOLEAN,
    ARRAY,
    OBJECT,
    ANY;

    /**
     * Checks if the given non-null value matches this type.
     *
     * @param value the object value to verify
     * @return true if the value matches the expected type
     */
    public boolean matches(Object value) {
        if (value == null) {
            return false;
        }
        return switch (this) {
            case STRING -> value instanceof CharSequence;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY -> value instanceof Collection<?> || value.getClass().isArray();
            case OBJECT -> value instanceof Map<?, ?>;
            case ANY -> true;
        };
    }
}
