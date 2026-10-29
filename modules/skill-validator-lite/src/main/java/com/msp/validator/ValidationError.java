package com.msp.validator;

import java.io.Serializable;

/**
 * Represents a single validation failure.
 *
 * @param field         the field or property name that failed validation
 * @param message       the error message explaining the failure
 * @param rejectedValue the invalid value that was tested
 */
public record ValidationError(String field, String message, Object rejectedValue) implements Serializable {

    public ValidationError {
        if (field == null || field.isBlank()) {
            field = "general";
        }
        if (message == null || message.isBlank()) {
            message = "Validation failed";
        }
    }

    public static ValidationError of(String field, String message) {
        return new ValidationError(field, message, null);
    }

    public static ValidationError of(String field, String message, Object rejectedValue) {
        return new ValidationError(field, message, rejectedValue);
    }
}
