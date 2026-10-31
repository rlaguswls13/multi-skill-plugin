package com.msp.pwdentropy;

import java.util.*;
import java.util.regex.Pattern;

/**
 * High-performance, zero-dependency password entropy and strength meter.
 * <p>
 * Evaluates password security using:
 * <ul>
 *   <li>Information theoretic pool-size entropy (E = L * log2(R))</li>
 *   <li>Shannon entropy based on character frequency distribution</li>
 *   <li>Common dictionary password detection</li>
 *   <li>Repetitive character sequence penalties (e.g. "aaaa", "1111")</li>
 *   <li>Sequential character run penalties (e.g. "abcdef", "123456", "qwerty")</li>
 * </ul>
 */
public final class PasswordEntropyMeter {

    public static final int LOWERCASE_POOL_SIZE = 26;
    public static final int UPPERCASE_POOL_SIZE = 26;
    public static final int DIGIT_POOL_SIZE = 10;
    public static final int SYMBOL_POOL_SIZE = 32;

    private static final double LOG_2 = Math.log(2.0);

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "123456", "12345678", "123456789", "12345",
            "admin", "qwerty", "letmein", "welcome", "monkey",
            "dragon", "iloveyou", "master", "guest", "default",
            "football", "superman", "login", "secret", "pass123",
            "abc123", "root", "testing", "changeme", "access"
    );

    private static final List<String> KEYBOARD_ROWS = List.of(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm",
            "1234567890"
    );

    private static final Pattern REPEAT_PATTERN = Pattern.compile("(.)\\1{2,}");

    private PasswordEntropyMeter() {
        // Utility class
    }

    /**
     * Evaluates the given password and returns a comprehensive entropy report.
     *
     * @param password the password to evaluate; null is treated as an empty password
     * @return the detailed {@link EntropyReport}
     */
    public static EntropyReport evaluate(String password) {
        if (password == null || password.isEmpty()) {
            return new EntropyReport(
                    0.0,
                    PasswordStrength.VERY_WEAK,
                    0,
                    0,
                    List.of("Password is empty"),
                    List.of("Provide a non-empty password with at least 12 characters")
            );
        }

        int length = password.length();
        int poolSize = calculatePoolSize(password);

        double poolEntropy = calculatePoolEntropy(password, poolSize);
        double shannonEntropy = calculateShannonEntropy(password);

        // Effective entropy starts at the pool entropy or Shannon entropy bound
        double effectiveEntropy = Math.min(poolEntropy, shannonEntropy * length);

        List<String> warnings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        boolean hasDictionaryWord = false;
        String lowerPassword = password.toLowerCase(Locale.ROOT);
        for (String common : COMMON_PASSWORDS) {
            if (lowerPassword.equals(common) || (common.length() >= 4 && lowerPassword.contains(common))) {
                hasDictionaryWord = true;
                warnings.add("Password contains commonly breached dictionary word/pattern: '" + common + "'");
                break;
            }
        }

        boolean hasRepeats = hasRepetitiveSequences(password);
        if (hasRepeats) {
            warnings.add("Password contains 3 or more repeating consecutive characters");
        }

        boolean hasSequential = hasSequentialRuns(password);
        if (hasSequential) {
            warnings.add("Password contains predictable sequential character runs (e.g. 'abc' or '123' or keyboard sequence)");
        }

        // Apply penalties
        if (hasDictionaryWord) {
            effectiveEntropy = Math.min(effectiveEntropy * 0.25, 15.0);
        }
        if (hasRepeats) {
            effectiveEntropy = Math.max(0.0, effectiveEntropy - 12.0);
        }
        if (hasSequential) {
            effectiveEntropy = Math.max(0.0, effectiveEntropy - 10.0);
        }

        // Length checks
        if (length < 8) {
            warnings.add("Password length is too short (" + length + " characters; minimum 8 required, 12+ recommended)");
            suggestions.add("Increase password length to at least 12 characters");
            // Penalty for critically short passwords
            effectiveEntropy = Math.min(effectiveEntropy, 25.0);
        } else if (length < 12) {
            suggestions.add("Increasing password length to 12 or more characters will significantly boost entropy");
        }

        // Character diversity suggestions
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (int i = 0; i < length; i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }

        if (!hasLower) suggestions.add("Add lowercase letters (a-z)");
        if (!hasUpper) suggestions.add("Add uppercase letters (A-Z)");
        if (!hasDigit) suggestions.add("Add numeric digits (0-9)");
        if (!hasSymbol) suggestions.add("Add special symbols or punctuation (!@#$%...)");
        if (hasDictionaryWord) suggestions.add("Avoid dictionary words, predictable names, and common words");
        if (hasRepeats || hasSequential) suggestions.add("Avoid consecutive repetitive or sequential character sequences");

        // Determine Strength rating
        PasswordStrength strength;
        if (hasDictionaryWord || length < 6 || effectiveEntropy < 28.0) {
            strength = PasswordStrength.VERY_WEAK;
        } else if (effectiveEntropy < 36.0 || length < 8) {
            strength = PasswordStrength.WEAK;
        } else if (effectiveEntropy < 60.0) {
            strength = PasswordStrength.FAIR;
        } else if (effectiveEntropy < 80.0) {
            strength = PasswordStrength.STRONG;
        } else {
            strength = PasswordStrength.VERY_STRONG;
        }

        double roundedEntropy = Math.round(effectiveEntropy * 100.0) / 100.0;
        return new EntropyReport(roundedEntropy, strength, poolSize, length, warnings, suggestions);
    }

    /**
     * Calculates the character pool size R based on character types present.
     */
    public static int calculatePoolSize(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }

        int poolSize = 0;
        if (hasLower) poolSize += LOWERCASE_POOL_SIZE;
        if (hasUpper) poolSize += UPPERCASE_POOL_SIZE;
        if (hasDigit) poolSize += DIGIT_POOL_SIZE;
        if (hasSymbol) poolSize += SYMBOL_POOL_SIZE;
        return poolSize;
    }

    /**
     * Calculates pool-size entropy in bits: E = L * log2(R).
     */
    public static double calculatePoolEntropy(String password) {
        if (password == null || password.isEmpty()) {
            return 0.0;
        }
        return calculatePoolEntropy(password, calculatePoolSize(password));
    }

    private static double calculatePoolEntropy(String password, int poolSize) {
        if (password == null || password.isEmpty() || poolSize <= 1) {
            return 0.0;
        }
        return password.length() * (Math.log(poolSize) / LOG_2);
    }

    /**
     * Calculates Shannon entropy (bits per character): H = - sum(p * log2(p)).
     */
    public static double calculateShannonEntropy(String password) {
        if (password == null || password.isEmpty()) {
            return 0.0;
        }
        int length = password.length();
        Map<Character, Integer> freq = new HashMap<>();
        for (int i = 0; i < length; i++) {
            freq.merge(password.charAt(i), 1, Integer::sum);
        }

        double entropy = 0.0;
        for (int count : freq.values()) {
            double p = (double) count / length;
            entropy -= p * (Math.log(p) / LOG_2);
        }
        return entropy;
    }

    /**
     * Checks if the password contains 3 or more identical consecutive characters.
     */
    public static boolean hasRepetitiveSequences(String password) {
        if (password == null || password.length() < 3) {
            return false;
        }
        return REPEAT_PATTERN.matcher(password).find();
    }

    /**
     * Checks if the password contains 3 or more sequential characters (e.g. "abc", "321", "qwerty").
     */
    public static boolean hasSequentialRuns(String password) {
        if (password == null || password.length() < 3) {
            return false;
        }
        String lower = password.toLowerCase(Locale.ROOT);

        // Check ASCII sequences (e.g. abc, 123, cba, 321)
        int runLength = 1;
        int lastDiff = 0;
        for (int i = 1; i < lower.length(); i++) {
            int diff = lower.charAt(i) - lower.charAt(i - 1);
            if ((diff == 1 || diff == -1) && (lastDiff == diff || runLength == 1)) {
                runLength++;
                lastDiff = diff;
                if (runLength >= 3) {
                    return true;
                }
            } else {
                runLength = 1;
                lastDiff = 0;
            }
        }

        // Check keyboard row sequences (e.g. "qwer", "asdf")
        for (String row : KEYBOARD_ROWS) {
            String reverseRow = new StringBuilder(row).reverse().toString();
            for (int i = 0; i <= lower.length() - 3; i++) {
                String sub = lower.substring(i, i + 3);
                if (row.contains(sub) || reverseRow.contains(sub)) {
                    return true;
                }
            }
        }

        return false;
    }
}
