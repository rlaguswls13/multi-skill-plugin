package com.msp.isbn;

import java.util.Objects;
import java.util.Optional;

/**
 * Validator, checksum calculator, and format converter for ISBN-10 and ISBN-13 standard book numbers.
 */
public final class IsbnValidator {

    private static final int[] ISBN_13_WEIGHTS = {1, 3, 1, 3, 1, 3, 1, 3, 1, 3, 1, 3};

    private IsbnValidator() {
    }

    /**
     * Checks if the given string is a valid ISBN-10 or ISBN-13.
     */
    public static boolean isValid(String isbn) {
        if (isbn == null) {
            return false;
        }
        String clean = IsbnFormatter.clean(isbn);
        if (clean.length() == 10) {
            return isValidIsbn10(clean);
        } else if (clean.length() == 13) {
            return isValidIsbn13(clean);
        }
        return false;
    }

    /**
     * Checks if the given string is a valid ISBN-10 (with or without hyphens).
     */
    public static boolean isValidIsbn10(String isbn) {
        if (isbn == null) {
            return false;
        }
        String clean = IsbnFormatter.clean(isbn);
        if (clean.length() != 10 || clean.equals("0000000000")) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char c = clean.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            sum += (c - '0') * (10 - i);
        }

        char last = clean.charAt(9);
        int checkVal;
        if (last == 'X') {
            checkVal = 10;
        } else if (last >= '0' && last <= '9') {
            checkVal = last - '0';
        } else {
            return false;
        }

        sum += checkVal * 1;
        return sum % 11 == 0;
    }

    /**
     * Checks if the given string is a valid ISBN-13 (with or without hyphens).
     */
    public static boolean isValidIsbn13(String isbn) {
        if (isbn == null) {
            return false;
        }
        String clean = IsbnFormatter.clean(isbn);
        if (clean.length() != 13) {
            return false;
        }

        // Must start with Bookland prefix 978 or 979
        if (!clean.startsWith("978") && !clean.startsWith("979")) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = clean.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int d = c - '0';
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += d * weight;
        }

        return sum % 10 == 0;
    }

    /**
     * Calculates the ISBN-10 check digit ('0'-'9' or 'X') for the first 9 digits.
     */
    public static char calculateCheckDigit10(String first9Digits) {
        Objects.requireNonNull(first9Digits, "first9Digits must not be null");
        String clean = IsbnFormatter.clean(first9Digits);
        if (clean.length() != 9) {
            throw new IllegalArgumentException("Expected exactly 9 digits, got: " + clean.length());
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char c = clean.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Character at index " + i + " is not a digit: " + c);
            }
            sum += (c - '0') * (10 - i);
        }

        int remainder = sum % 11;
        int checkVal = (11 - remainder) % 11;
        return checkVal == 10 ? 'X' : (char) ('0' + checkVal);
    }

    /**
     * Calculates the ISBN-13 check digit (0-9) for the first 12 digits.
     */
    public static int calculateCheckDigit13(String first12Digits) {
        Objects.requireNonNull(first12Digits, "first12Digits must not be null");
        String clean = IsbnFormatter.clean(first12Digits);
        if (clean.length() != 12) {
            throw new IllegalArgumentException("Expected exactly 12 digits, got: " + clean.length());
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            char c = clean.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Character at index " + i + " is not a digit: " + c);
            }
            sum += (c - '0') * ISBN_13_WEIGHTS[i];
        }

        int remainder = sum % 10;
        return (10 - remainder) % 10;
    }

    /**
     * Converts a valid ISBN-10 to an ISBN-13 (prefixed with 978 and recalculated check digit).
     */
    public static String convertIsbn10To13(String isbn10) {
        Objects.requireNonNull(isbn10, "isbn10 must not be null");
        String clean = IsbnFormatter.clean(isbn10);
        if (!isValidIsbn10(clean)) {
            throw new IllegalArgumentException("Invalid ISBN-10: " + isbn10);
        }

        String core12 = "978" + clean.substring(0, 9);
        int checkDigit = calculateCheckDigit13(core12);
        return core12 + checkDigit;
    }

    /**
     * Converts a valid 978-prefixed ISBN-13 to an ISBN-10.
     * Returns Optional.empty() if the ISBN-13 has a 979 prefix, as 979 cannot be mapped to ISBN-10.
     */
    public static Optional<String> convertIsbn13To10(String isbn13) {
        Objects.requireNonNull(isbn13, "isbn13 must not be null");
        String clean = IsbnFormatter.clean(isbn13);
        if (!isValidIsbn13(clean)) {
            throw new IllegalArgumentException("Invalid ISBN-13: " + isbn13);
        }

        if (!clean.startsWith("978")) {
            return Optional.empty();
        }

        String core9 = clean.substring(3, 12);
        char checkDigit = calculateCheckDigit10(core9);
        return Optional.of(core9 + checkDigit);
    }

    /**
     * Validates and returns full ISBN metadata including cross-conversions.
     */
    public static IsbnInfo validate(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return IsbnInfo.failure(isbn, "ISBN must not be null or blank");
        }
        String clean = IsbnFormatter.clean(isbn);

        if (clean.length() == 10) {
            if (!isValidIsbn10(clean)) {
                return IsbnInfo.failure(isbn, "Invalid ISBN-10 checksum");
            }
            String isbn13 = convertIsbn10To13(clean);
            return IsbnInfo.success(isbn, clean, IsbnType.ISBN_10, clean, isbn13);
        } else if (clean.length() == 13) {
            if (!isValidIsbn13(clean)) {
                return IsbnInfo.failure(isbn, "Invalid ISBN-13 checksum or non-bookland prefix");
            }
            String isbn10 = convertIsbn13To10(clean).orElse(null);
            return IsbnInfo.success(isbn, clean, IsbnType.ISBN_13, isbn10, clean);
        } else {
            return IsbnInfo.failure(isbn, "Invalid ISBN length: " + clean.length() + " characters (expected 10 or 13)");
        }
    }
}
