package com.msp.jsonschema;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable constraint definition for a JSON/Map field.
 *
 * @param required  whether the field is mandatory and cannot be null
 * @param type      expected field data type
 * @param minLength minimum string length or minimum array item count
 * @param maxLength maximum string length or maximum array item count
 * @param min       minimum numeric value (inclusive)
 * @param max       maximum numeric value (inclusive)
 * @param pattern   regex pattern string for string validation
 */
public record FieldConstraint(
        boolean required,
        JsonFieldType type,
        Integer minLength,
        Integer maxLength,
        Double min,
        Double max,
        String pattern
) {
    public FieldConstraint {
        if (type == null) {
            type = JsonFieldType.ANY;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static FieldConstraint required(JsonFieldType type) {
        return builder().required(true).type(type).build();
    }

    public static FieldConstraint optional(JsonFieldType type) {
        return builder().required(false).type(type).build();
    }

    public static class Builder {
        private boolean required = false;
        private JsonFieldType type = JsonFieldType.ANY;
        private Integer minLength;
        private Integer maxLength;
        private Double min;
        private Double max;
        private String pattern;

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder required() {
            this.required = true;
            return this;
        }

        public Builder optional() {
            this.required = false;
            return this;
        }

        public Builder type(JsonFieldType type) {
            this.type = Objects.requireNonNullElse(type, JsonFieldType.ANY);
            return this;
        }

        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder min(double min) {
            this.min = min;
            return this;
        }

        public Builder max(double max) {
            this.max = max;
            return this;
        }

        public Builder pattern(String regex) {
            this.pattern = regex;
            return this;
        }

        public Builder pattern(Pattern pattern) {
            this.pattern = pattern != null ? pattern.pattern() : null;
            return this;
        }

        public FieldConstraint build() {
            return new FieldConstraint(required, type, minLength, maxLength, min, max, pattern);
        }
    }
}
