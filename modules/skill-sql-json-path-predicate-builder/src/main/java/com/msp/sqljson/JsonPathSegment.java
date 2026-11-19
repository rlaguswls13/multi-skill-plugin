package com.msp.sqljson;

import java.util.Objects;

/**
 * A single validated step inside a {@link JsonPath}.
 */
public sealed interface JsonPathSegment permits JsonPathSegment.Property, JsonPathSegment.ArrayIndex {

    /**
     * Object property access segment (e.g. {@code user}, {@code first_name}).
     */
    record Property(String name) implements JsonPathSegment {
        public Property {
            Objects.requireNonNull(name, "Property name must not be null");
            SqlIdentifierValidator.validateJsonProperty(name);
        }
    }

    /**
     * Zero-based array index access segment (e.g. {@code [0]}).
     */
    record ArrayIndex(int index) implements JsonPathSegment {
        public ArrayIndex {
            if (index < 0) {
                throw new IllegalArgumentException("Array index must be non-negative: " + index);
            }
        }
    }
}
