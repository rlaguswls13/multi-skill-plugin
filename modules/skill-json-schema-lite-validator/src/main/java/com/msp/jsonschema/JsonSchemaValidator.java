package com.msp.jsonschema;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Ultra-lightweight schema validator for Map/JSON structures without external dependencies.
 * Thread-safe for evaluation and field definition.
 */
public class JsonSchemaValidator {

    private final Map<String, FieldConstraint> fieldConstraints = new ConcurrentHashMap<>();
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    public JsonSchemaValidator() {
    }

    /**
     * Defines or updates a field constraint in this schema validator.
     *
     * @param fieldName  the JSON property name
     * @param constraint the constraint rules for the field
     * @return this validator for fluent chaining
     */
    public JsonSchemaValidator defineField(String fieldName, FieldConstraint constraint) {
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        Objects.requireNonNull(constraint, "constraint must not be null");
        fieldConstraints.put(fieldName, constraint);
        if (constraint.pattern() != null) {
            compiledPatterns.put(constraint.pattern(), Pattern.compile(constraint.pattern()));
        }
        return this;
    }

    /**
     * Validates the provided JSON/Map data against all defined field constraints.
     *
     * @param data the key-value map representing the JSON object
     * @return ValidationResult indicating success or list of errors
     */
    public ValidationResult validate(Map<String, ?> data) {
        List<String> errors = new ArrayList<>();
        if (data == null) {
            errors.add("Data map must not be null");
            return ValidationResult.invalid(errors);
        }

        for (Map.Entry<String, FieldConstraint> entry : fieldConstraints.entrySet()) {
            String fieldName = entry.getKey();
            FieldConstraint constraint = entry.getValue();

            boolean present = data.containsKey(fieldName);
            Object value = data.get(fieldName);

            if (constraint.required() && (!present || value == null)) {
                errors.add("Required field '" + fieldName + "' is missing or null");
                continue;
            }

            if (!present || value == null) {
                // Optional field that is not present or null is valid
                continue;
            }

            // Type check
            if (constraint.type() != JsonFieldType.ANY && !constraint.type().matches(value)) {
                errors.add("Field '" + fieldName + "' expected type " + constraint.type()
                        + " but found " + value.getClass().getSimpleName());
                continue;
            }

            // String constraints
            if (value instanceof CharSequence seq) {
                String str = seq.toString();
                if (constraint.minLength() != null && str.length() < constraint.minLength()) {
                    errors.add("Field '" + fieldName + "' length (" + str.length()
                            + ") is less than minimum length " + constraint.minLength());
                }
                if (constraint.maxLength() != null && str.length() > constraint.maxLength()) {
                    errors.add("Field '" + fieldName + "' length (" + str.length()
                            + ") exceeds maximum length " + constraint.maxLength());
                }
                if (constraint.pattern() != null) {
                    Pattern pattern = compiledPatterns.computeIfAbsent(constraint.pattern(), Pattern::compile);
                    if (!pattern.matcher(str).matches()) {
                        errors.add("Field '" + fieldName + "' does not match pattern: " + constraint.pattern());
                    }
                }
            }

            // Number constraints
            if (value instanceof Number num) {
                double val = num.doubleValue();
                if (constraint.min() != null && val < constraint.min()) {
                    errors.add("Field '" + fieldName + "' value (" + val
                            + ") is less than minimum allowed " + constraint.min());
                }
                if (constraint.max() != null && val > constraint.max()) {
                    errors.add("Field '" + fieldName + "' value (" + val
                            + ") exceeds maximum allowed " + constraint.max());
                }
            }

            // Array item count constraints
            if (constraint.type() == JsonFieldType.ARRAY) {
                int size = -1;
                if (value instanceof Collection<?> col) {
                    size = col.size();
                } else if (value.getClass().isArray()) {
                    size = java.lang.reflect.Array.getLength(value);
                }
                if (size >= 0) {
                    if (constraint.minLength() != null && size < constraint.minLength()) {
                        errors.add("Array field '" + fieldName + "' size (" + size
                                + ") is less than minimum required " + constraint.minLength());
                    }
                    if (constraint.maxLength() != null && size > constraint.maxLength()) {
                        errors.add("Array field '" + fieldName + "' size (" + size
                                + ") exceeds maximum allowed " + constraint.maxLength());
                    }
                }
            }
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(errors);
    }
}
