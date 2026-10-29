package com.msp.biznum;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Korean Corporate Registration Number (법인등록번호, 13자리) checksum validator and classifier.
 * Format: XXXXXX-XXXXXXX (6-7 digits).
 * Algorithm uses weighted alternating checksum [1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2] modulo 10.
 */
public final class CorpNumberValidator {

    private static final int[] WEIGHTS = {1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2};
    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^0-9]");
    private static final Pattern FORMATTED_PATTERN = Pattern.compile("^\\d{6}-\\d{7}$");

    private CorpNumberValidator() {
    }

    /**
     * Result of validating a corporate registration number.
     */
    public record Result(
            boolean valid,
            String raw,
            String formatted,
            CorporateType corporateType,
            String registryOfficeCode,
            String errorMessage
    ) {
        public static Result success(String raw, String formatted, CorporateType corporateType, String registryOfficeCode) {
            return new Result(true, raw, formatted, corporateType, registryOfficeCode, null);
        }

        public static Result failure(String raw, String errorMessage) {
            return new Result(false, raw, null, CorporateType.OTHER, null, errorMessage);
        }
    }

    /**
     * Checks if the given number is a valid Korean corporate registration number.
     * Accepts both hyphenated ("110111-1234567") and plain ("1101111234567") formats.
     */
    public static boolean isValid(String number) {
        if (number == null) {
            return false;
        }
        String digits = unformat(number);
        if (digits.length() != 13 || digits.equals("0000000000000")) {
            return false;
        }
        return verifyChecksum(digits);
    }

    /**
     * Validates and extracts corporate type and registry office info.
     */
    public static Result validate(String number) {
        if (number == null || number.isBlank()) {
            return Result.failure(number, "Corporate registration number must not be null or blank");
        }
        String digits = unformat(number);
        if (digits.length() != 13) {
            return Result.failure(number, "Corporate registration number must be 13 digits (got " + digits.length() + ")");
        }
        if (digits.equals("0000000000000")) {
            return Result.failure(number, "Corporate registration number cannot be all zeros");
        }

        int expectedCheckDigit = calculateCheckDigit(digits.substring(0, 12));
        int actualCheckDigit = digits.charAt(12) - '0';
        if (expectedCheckDigit != actualCheckDigit) {
            return Result.failure(number, "Checksum mismatch: expected " + expectedCheckDigit + ", got " + actualCheckDigit);
        }

        CorporateType type = identifyCorporateType(digits);
        String registryOfficeCode = digits.substring(0, 4);
        String formatted = format(digits);
        return Result.success(digits, formatted, type, registryOfficeCode);
    }

    /**
     * Calculates the 13th check digit from the first 12 digits.
     *
     * @param first12Digits 12-digit string
     * @return check digit (0-9)
     */
    public static int calculateCheckDigit(String first12Digits) {
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

        int remainder = sum % 10;
        return (10 - remainder) % 10;
    }

    /**
     * Identifies the corporate type from digits 5 and 6 (index 4 and 5).
     */
    public static CorporateType identifyCorporateType(String number) {
        if (number == null) {
            return CorporateType.OTHER;
        }
        String digits = unformat(number);
        if (digits.length() < 6) {
            return CorporateType.OTHER;
        }
        return CorporateType.fromCode(digits.substring(4, 6));
    }

    /**
     * Formats 13 digits into "XXXXXX-XXXXXXX".
     */
    public static String format(String rawNumber) {
        if (rawNumber == null) {
            return null;
        }
        String digits = unformat(rawNumber);
        if (digits.length() != 13) {
            return rawNumber;
        }
        return digits.substring(0, 6) + "-" + digits.substring(6, 13);
    }

    /**
     * Strips non-digit characters from the input.
     */
    public static String unformat(String formattedNumber) {
        if (formattedNumber == null) {
            return "";
        }
        return CLEAN_PATTERN.matcher(formattedNumber).replaceAll("");
    }

    /**
     * Checks if the format strictly conforms to "XXXXXX-XXXXXXX".
     */
    public static boolean hasValidPattern(String number) {
        return number != null && FORMATTED_PATTERN.matcher(number).matches();
    }

    private static boolean verifyChecksum(String digits) {
        try {
            int expected = calculateCheckDigit(digits.substring(0, 12));
            int actual = digits.charAt(12) - '0';
            return expected == actual;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
