package com.msp.pwdentropy;

import java.util.List;
import java.util.Objects;

/**
 * Detailed evaluation report of a password's entropy and strength.
 *
 * @param entropyBits Calculated effective entropy in bits.
 * @param strength    Overall qualitative strength rating.
 * @param poolSize    Total size of the character pool detected.
 * @param length      Length of the analyzed password.
 * @param warnings    List of detected vulnerabilities and risk warnings.
 * @param suggestions List of actionable improvement suggestions.
 */
public record EntropyReport(
        double entropyBits,
        PasswordStrength strength,
        int poolSize,
        int length,
        List<String> warnings,
        List<String> suggestions
) {
    public EntropyReport {
        Objects.requireNonNull(strength, "strength must not be null");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    /**
     * Checks if the evaluated password meets a baseline security threshold (FAIR, STRONG, or VERY_STRONG).
     *
     * @return true if strength is at least FAIR
     */
    public boolean isAcceptable() {
        return strength == PasswordStrength.FAIR
                || strength == PasswordStrength.STRONG
                || strength == PasswordStrength.VERY_STRONG;
    }

    /**
     * Checks if the evaluated password meets a high security threshold (STRONG or VERY_STRONG).
     *
     * @return true if strength is STRONG or VERY_STRONG
     */
    public boolean isStrong() {
        return strength == PasswordStrength.STRONG
                || strength == PasswordStrength.VERY_STRONG;
    }
}
