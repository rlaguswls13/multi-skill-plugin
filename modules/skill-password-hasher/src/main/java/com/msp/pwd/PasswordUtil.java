package com.msp.pwd;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Unified facade for password hashing, constant-time verification, strength auditing,
 * and cryptographically secure random password generation.
 */
public final class PasswordUtil {

    private static final PasswordHasher DEFAULT_HASHER = new Pbkdf2PasswordHasher();
    private static final PasswordStrengthValidator DEFAULT_VALIDATOR = PasswordStrengthValidator.strong();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?";

    private PasswordUtil() {
        // Utility class
    }

    /**
     * Hashes a raw password using standard PBKDF2WithHmacSHA256 (65,536 iterations).
     */
    public static String hash(CharSequence rawPassword) {
        return DEFAULT_HASHER.hash(rawPassword);
    }

    /**
     * Verifies a raw password against an encoded PBKDF2 hash using constant-time comparison.
     */
    public static boolean verify(CharSequence rawPassword, String encodedHash) {
        return DEFAULT_HASHER.verify(rawPassword, encodedHash);
    }

    /**
     * Evaluates password strength against strong enterprise criteria.
     */
    public static PasswordStrengthResult validateStrength(CharSequence rawPassword) {
        return DEFAULT_VALIDATOR.validate(rawPassword);
    }

    /**
     * Checks if a password meets strong enterprise criteria.
     */
    public static boolean isStrong(CharSequence rawPassword) {
        return DEFAULT_VALIDATOR.validate(rawPassword).isValid();
    }

    /**
     * Generates a cryptographically secure random password guaranteed to contain
     * a mix of character classes.
     *
     * @param length         total length (minimum 4)
     * @param includeSpecial whether to include special characters
     * @return secure random password string
     */
    public static String generateRandomPassword(int length, boolean includeSpecial) {
        int minRequired = includeSpecial ? 4 : 3;
        if (length < minRequired) {
            throw new IllegalArgumentException("Length must be at least " + minRequired + " to satisfy character classes");
        }

        List<Character> chars = new ArrayList<>(length);
        chars.add(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));

        String pool = UPPER + LOWER + DIGITS;
        if (includeSpecial) {
            chars.add(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
            pool += SPECIAL;
        }

        while (chars.size() < length) {
            chars.add(pool.charAt(RANDOM.nextInt(pool.length())));
        }

        // Secure Fisher-Yates shuffle
        for (int i = chars.size() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            Collections.swap(chars, i, j);
        }

        StringBuilder sb = new StringBuilder(length);
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
