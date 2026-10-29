package com.msp.validator;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception thrown when validation rules are violated.
 */
public class ValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public ValidationException(List<ValidationError> errors) {
        super(formatMessage(errors));
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public ValidationException(String message, List<ValidationError> errors) {
        super(message);
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    private static String formatMessage(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Validation failed";
        }
        String details = errors.stream()
                .map(e -> "[field: " + e.field() + ", error: " + e.message() + "]")
                .collect(Collectors.joining(", "));
        return "Validation failed with " + errors.size() + " error(s): " + details;
    }
}
