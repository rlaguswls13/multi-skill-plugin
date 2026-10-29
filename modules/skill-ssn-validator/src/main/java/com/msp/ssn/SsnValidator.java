package com.msp.ssn;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Korean Resident Registration Number (주민등록번호) and
 * Foreigner Registration Number (외국인등록번호) checksum validator and metadata parser.
 * Format: XXXXXX-XXXXXXX (13 digits).
 */
public final class SsnValidator {

    private static final int[] WEIGHTS = {2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5};
    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^0-9]");
    private static final Pattern FORMATTED_PATTERN = Pattern.compile("^\\d{6}-\\d{7}$");

    private SsnValidator() {
    }

    /**
     * Checks if the given 13-digit registration number is valid (format, birthdate, and checksum).
     */
    public static boolean isValid(String ssn) {
        if (ssn == null) {
            return false;
        }
        String digits = unformat(ssn);
        if (digits.length() != 13 || digits.equals("0000000000000")) {
            return false;
        }

        char genderChar = digits.charAt(6);
        int century = resolveCentury(genderChar);
        if (century == 0) {
            return false;
        }

        if (!isValidBirthDate(digits, century)) {
            return false;
        }

        boolean foreigner = isForeignerCode(genderChar);
        int expectedCheckDigit = calculateCheckDigit(digits.substring(0, 12), foreigner);
        int actualCheckDigit = digits.charAt(12) - '0';

        return expectedCheckDigit == actualCheckDigit;
    }

    /**
     * Parses and validates the registration number, returning detailed metadata.
     */
    public static SsnInfo validate(String ssn) {
        if (ssn == null || ssn.isBlank()) {
            return SsnInfo.failure(ssn, "SSN must not be null or blank");
        }
        String digits = unformat(ssn);
        if (digits.length() != 13) {
            return SsnInfo.failure(ssn, "SSN must be 13 digits (got " + digits.length() + ")");
        }
        if (digits.equals("0000000000000")) {
            return SsnInfo.failure(ssn, "SSN cannot be all zeros");
        }

        char genderChar = digits.charAt(6);
        int century = resolveCentury(genderChar);
        if (century == 0) {
            return SsnInfo.failure(ssn, "Invalid gender digit: '" + genderChar + "'");
        }

        LocalDate birthDate;
        try {
            int year = century + Integer.parseInt(digits.substring(0, 2));
            int month = Integer.parseInt(digits.substring(2, 4));
            int day = Integer.parseInt(digits.substring(4, 6));
            birthDate = LocalDate.of(year, month, day);
        } catch (DateTimeException | NumberFormatException e) {
            return SsnInfo.failure(ssn, "Invalid birth date encoded in SSN: " + e.getMessage());
        }

        boolean foreigner = isForeignerCode(genderChar);
        Gender gender = resolveGender(genderChar);

        int expectedCheckDigit = calculateCheckDigit(digits.substring(0, 12), foreigner);
        int actualCheckDigit = digits.charAt(12) - '0';
        if (expectedCheckDigit != actualCheckDigit) {
            return SsnInfo.failure(ssn, "Checksum mismatch: expected " + expectedCheckDigit + ", got " + actualCheckDigit);
        }

        String formatted = format(digits);
        return SsnInfo.success(digits, formatted, birthDate, gender, foreigner, century);
    }

    /**
     * Calculates the 13th check digit from the first 12 digits.
     */
    public static int calculateCheckDigit(String first12Digits, boolean foreigner) {
        Objects.requireNonNull(first12Digits, "first12Digits must not be null");
        String digits = unformat(first12Digits);
        if (digits.length() != 12) {
            throw new IllegalArgumentException("First 12 digits required, got: " + digits.length());
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = digits.charAt(i) - '0';
            sum += d * WEIGHTS[i];
        }

        int remainder = (11 - (sum % 11)) % 10;
        if (foreigner) {
            return (remainder + 2) % 10;
        }
        return remainder;
    }

    /**
     * Extracts the birth date from SSN if valid, without checking checksum.
     */
    public static Optional<LocalDate> extractBirthDate(String ssn) {
        if (ssn == null) {
            return Optional.empty();
        }
        String digits = unformat(ssn);
        if (digits.length() < 7) {
            return Optional.empty();
        }
        int century = resolveCentury(digits.charAt(6));
        if (century == 0) {
            return Optional.empty();
        }
        try {
            int year = century + Integer.parseInt(digits.substring(0, 2));
            int month = Integer.parseInt(digits.substring(2, 4));
            int day = Integer.parseInt(digits.substring(4, 6));
            return Optional.of(LocalDate.of(year, month, day));
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Extracts gender from SSN based on 7th digit.
     */
    public static Optional<Gender> extractGender(String ssn) {
        if (ssn == null) {
            return Optional.empty();
        }
        String digits = unformat(ssn);
        if (digits.length() < 7) {
            return Optional.empty();
        }
        Gender gender = resolveGender(digits.charAt(6));
        return Optional.ofNullable(gender);
    }

    /**
     * Checks if the 7th digit indicates a foreigner registration number (5, 6, 7, 8).
     */
    public static boolean isForeigner(String ssn) {
        if (ssn == null) {
            return false;
        }
        String digits = unformat(ssn);
        if (digits.length() < 7) {
            return false;
        }
        return isForeignerCode(digits.charAt(6));
    }

    /**
     * Formats 13 digits to "XXXXXX-XXXXXXX".
     */
    public static String format(String rawSsn) {
        if (rawSsn == null) {
            return null;
        }
        String digits = unformat(rawSsn);
        if (digits.length() != 13) {
            return rawSsn;
        }
        return digits.substring(0, 6) + "-" + digits.substring(6, 13);
    }

    /**
     * Strips non-digit characters from the input.
     */
    public static String unformat(String formattedSsn) {
        if (formattedSsn == null) {
            return "";
        }
        return CLEAN_PATTERN.matcher(formattedSsn).replaceAll("");
    }

    /**
     * Checks if the format conforms to "XXXXXX-XXXXXXX".
     */
    public static boolean hasValidPattern(String ssn) {
        return ssn != null && FORMATTED_PATTERN.matcher(ssn).matches();
    }

    private static int resolveCentury(char genderChar) {
        return switch (genderChar) {
            case '9', '0' -> 1800;
            case '1', '2', '5', '6' -> 1900;
            case '3', '4', '7', '8' -> 2000;
            default -> 0;
        };
    }

    private static Gender resolveGender(char genderChar) {
        return switch (genderChar) {
            case '1', '3', '5', '7', '9' -> Gender.MALE;
            case '2', '4', '6', '8', '0' -> Gender.FEMALE;
            default -> null;
        };
    }

    private static boolean isForeignerCode(char genderChar) {
        return genderChar >= '5' && genderChar <= '8';
    }

    private static boolean isValidBirthDate(String digits, int century) {
        try {
            int year = century + Integer.parseInt(digits.substring(0, 2));
            int month = Integer.parseInt(digits.substring(2, 4));
            int day = Integer.parseInt(digits.substring(4, 6));
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException | NumberFormatException e) {
            return false;
        }
    }
}
