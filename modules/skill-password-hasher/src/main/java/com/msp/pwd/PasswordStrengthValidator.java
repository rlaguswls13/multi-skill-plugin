package com.msp.pwd;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates password strength and calculates security scores based on configurable rules.
 */
public final class PasswordStrengthValidator {

    private int minLength = 8;
    private int maxLength = 128;
    private boolean requireUppercase = true;
    private boolean requireLowercase = true;
    private boolean requireDigit = true;
    private boolean requireSpecialChar = true;
    private boolean disallowWhitespace = true;

    public PasswordStrengthValidator() {
    }

    /**
     * Standard preset: min 8 chars, uppercase, lowercase, digit required.
     */
    public static PasswordStrengthValidator standard() {
        return new PasswordStrengthValidator()
                .minLength(8)
                .maxLength(128)
                .requireUppercase(true)
                .requireLowercase(true)
                .requireDigit(true)
                .requireSpecialChar(false)
                .disallowWhitespace(true);
    }

    /**
     * Strong enterprise preset: min 10 chars, uppercase, lowercase, digit, special char required.
     */
    public static PasswordStrengthValidator strong() {
        return new PasswordStrengthValidator()
                .minLength(10)
                .maxLength(128)
                .requireUppercase(true)
                .requireLowercase(true)
                .requireDigit(true)
                .requireSpecialChar(true)
                .disallowWhitespace(true);
    }

    /**
     * Lenient preset: min 6 chars, no character class restrictions.
     */
    public static PasswordStrengthValidator lenient() {
        return new PasswordStrengthValidator()
                .minLength(6)
                .maxLength(128)
                .requireUppercase(false)
                .requireLowercase(false)
                .requireDigit(false)
                .requireSpecialChar(false)
                .disallowWhitespace(false);
    }

    public PasswordStrengthValidator minLength(int minLength) {
        this.minLength = minLength;
        return this;
    }

    public PasswordStrengthValidator maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public PasswordStrengthValidator requireUppercase(boolean requireUppercase) {
        this.requireUppercase = requireUppercase;
        return this;
    }

    public PasswordStrengthValidator requireLowercase(boolean requireLowercase) {
        this.requireLowercase = requireLowercase;
        return this;
    }

    public PasswordStrengthValidator requireDigit(boolean requireDigit) {
        this.requireDigit = requireDigit;
        return this;
    }

    public PasswordStrengthValidator requireSpecialChar(boolean requireSpecialChar) {
        this.requireSpecialChar = requireSpecialChar;
        return this;
    }

    public PasswordStrengthValidator disallowWhitespace(boolean disallowWhitespace) {
        this.disallowWhitespace = disallowWhitespace;
        return this;
    }

    /**
     * Evaluates the password against configured rules and assigns a strength score (0-100).
     */
    public PasswordStrengthResult validate(CharSequence password) {
        List<String> violations = new ArrayList<>();

        if (password == null) {
            violations.add("Password must not be null.");
            return new PasswordStrengthResult(false, 0, StrengthLevel.VERY_WEAK, violations);
        }

        int len = password.length();
        if (len < minLength) {
            violations.add("Password must be at least " + minLength + " characters long (got " + len + ").");
        }
        if (len > maxLength) {
            violations.add("Password must not exceed " + maxLength + " characters (got " + len + ").");
        }

        int upperCount = 0;
        int lowerCount = 0;
        int digitCount = 0;
        int specialCount = 0;
        boolean hasWhitespace = false;

        for (int i = 0; i < len; i++) {
            char c = password.charAt(i);
            if (Character.isWhitespace(c)) {
                hasWhitespace = true;
            } else if (Character.isUpperCase(c)) {
                upperCount++;
            } else if (Character.isLowerCase(c)) {
                lowerCount++;
            } else if (Character.isDigit(c)) {
                digitCount++;
            } else {
                specialCount++;
            }
        }

        if (disallowWhitespace && hasWhitespace) {
            violations.add("Password must not contain any whitespace characters.");
        }
        if (requireUppercase && upperCount == 0) {
            violations.add("Password must contain at least one uppercase letter (A-Z).");
        }
        if (requireLowercase && lowerCount == 0) {
            violations.add("Password must contain at least one lowercase letter (a-z).");
        }
        if (requireDigit && digitCount == 0) {
            violations.add("Password must contain at least one digit (0-9).");
        }
        if (requireSpecialChar && specialCount == 0) {
            violations.add("Password must contain at least one special character.");
        }

        // Calculate score
        int score = calculateScore(len, upperCount, lowerCount, digitCount, specialCount, hasWhitespace);
        StrengthLevel level = determineLevel(score);
        boolean isValid = violations.isEmpty();

        return new PasswordStrengthResult(isValid, score, level, violations);
    }

    private int calculateScore(int len, int upper, int lower, int digit, int special, boolean whitespace) {
        if (len == 0) return 0;

        int score = 0;
        // Length factor (up to 40 pts)
        score += Math.min(40, len * 4);

        // Character class diversity (up to 60 pts)
        if (upper > 0) score += 15;
        if (lower > 0) score += 15;
        if (digit > 0) score += 15;
        if (special > 0) score += 15;

        // Variety bonus
        int classesPresent = (upper > 0 ? 1 : 0) + (lower > 0 ? 1 : 0) + (digit > 0 ? 1 : 0) + (special > 0 ? 1 : 0);
        if (classesPresent >= 4 && len >= 12) {
            score += 10;
        }

        if (whitespace && disallowWhitespace) {
            score = Math.max(0, score - 20);
        }

        return Math.min(100, Math.max(0, score));
    }

    private StrengthLevel determineLevel(int score) {
        if (score >= 80) return StrengthLevel.VERY_STRONG;
        if (score >= 60) return StrengthLevel.STRONG;
        if (score >= 40) return StrengthLevel.MEDIUM;
        if (score >= 20) return StrengthLevel.WEAK;
        return StrengthLevel.VERY_WEAK;
    }
}
