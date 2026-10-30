package com.msp.jsonschema;

import java.util.List;

/**
 * Result of a JSON/Map schema validation run.
 *
 * @param isValid       true if validation passed without any errors
 * @param errorMessages immutable list of error messages (empty if valid)
 */
public record ValidationResult(boolean isValid, List<String> errorMessages) {
    public ValidationResult {
        errorMessages = errorMessages == null ? List.of() : List.copyOf(errorMessages);
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public static ValidationResult invalid(String... errors) {
        return new ValidationResult(false, List.of(errors));
    }

    public boolean hasErrors() {
        return !isValid;
    }
}
