package com.msp.validator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Result of a validation process containing success status and any validation errors.
 */
public record ValidationResult(List<ValidationError> errors) {

    private static final ValidationResult VALID = new ValidationResult(List.of());

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ValidationResult valid() {
        return VALID;
    }

    public static ValidationResult of(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return VALID;
        }
        return new ValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public Optional<ValidationError> firstError() {
        return errors.isEmpty() ? Optional.empty() : Optional.of(errors.getFirst());
    }

    public Optional<String> firstErrorMessage() {
        return errors.isEmpty() ? Optional.empty() : Optional.of(errors.getFirst().message());
    }

    public void throwIfInvalid() {
        if (hasErrors()) {
            throw new ValidationException(errors);
        }
    }

    public <X extends Throwable> void throwIfInvalid(Function<List<ValidationError>, X> exceptionSupplier) throws X {
        if (hasErrors()) {
            throw exceptionSupplier.apply(Collections.unmodifiableList(errors));
        }
    }
}
