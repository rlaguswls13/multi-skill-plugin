package com.msp.pwd;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of evaluating a password against strength criteria.
 */
public record PasswordStrengthResult(
        boolean isValid,
        int score,
        StrengthLevel level,
        List<String> violations
) {
    public PasswordStrengthResult {
        Objects.requireNonNull(level, "level must not be null");
        violations = violations != null ? Collections.unmodifiableList(violations) : Collections.emptyList();
    }
}
