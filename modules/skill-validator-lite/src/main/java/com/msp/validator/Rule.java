package com.msp.validator;

import java.util.Optional;

/**
 * Functional rule interface for validating a target object.
 *
 * @param <T> the type of object being validated
 */
@FunctionalInterface
public interface Rule<T> {

    /**
     * Validates the target object.
     *
     * @param target the object to validate
     * @return an {@link Optional} containing a {@link ValidationError} if invalid, or empty if valid.
     */
    Optional<ValidationError> validate(T target);
}
