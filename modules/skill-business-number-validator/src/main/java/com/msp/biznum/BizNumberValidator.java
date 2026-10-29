package com.msp.biznum;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Korean Business Registration Number (사업자등록번호, 10자리) checksum validator and utility.
 * Format: XXX-XX-XXXXX (3-2-5 digits).
 * Algorithm uses weighted checksum [1, 3, 7, 1, 3, 7, 1, 3, 5] modulo 10.
 */
public final class BizNumberValidator {

    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};
    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^0-9]");
    private static final Pattern FORMATTED_PATTERN = Pattern.compile("^\\d{3}-\\d{2}-\\d{5}$");

    private BizNumberValidator() {
    }

    /**
     * Result of validating a business registration number.
     */
    public record Result(
            boolean valid,
            String raw,
            String formatted,
            BizType bizType,
            String errorMessage
    ) {
        public static Result success(String raw, String formatted, BizType bizType) {
            return new Result(true, raw, formatted, bizType, null);
        }

        public static Result failure(String raw, String errorMessage) {
            return new Result(false, raw, null, BizType.UNKNOWN, errorMessage);
        }
    }

    /**
     * Checks if the given number is a valid Korean business registration number.
     * Accepts both hyphenated ("120-81-47521") and plain ("1208147521") formats.
     */
    public static boolean isValid(String number) {
        if (number == null) {
            return false;
        }
        String digits = unformat(number);
        if (digits.length() != 10 || digits.equals("0000000000")) {
            return false;
        }
        return verifyChecksum(digits);
    }

    /**
     * Validates and extracts metadata for the business registration number.
     */
    public static Result validate(String number) {
        if (number == null || number.isBlank()) {
            return Result.failure(number, "Business registration number must not be null or blank");
        }
        String digits = unformat(number);
        if (digits.length() != 10) {
            return Result.failure(number, "Business registration number must be 10 digits (got " + digits.length() + ")");
        }
        if (digits.equals("0000000000")) {
            return Result.failure(number, "Business registration number cannot be all zeros");
        }

        int expectedCheckDigit = calculateCheckDigit(digits.substring(0, 9));
        int actualCheckDigit = digits.charAt(9) - '0';
        if (expectedCheckDigit != actualCheckDigit) {
            return Result.failure(number, "Checksum mismatch: expected " + expectedCheckDigit + ", got " + actualCheckDigit);
        }

        BizType bizType = getBizType(digits);
        String formatted = format(digits);
        return Result.success(digits, formatted, bizType);
    }

    /**
     * Calculates the 10th check digit from the first 9 digits.
     *
     * @param first9Digits 9-digit string
     * @return check digit (0-9)
     */
    public static int calculateCheckDigit(String first9Digits) {
        Objects.requireNonNull(first9Digits, "first9Digits must not be null");
        String digits = unformat(first9Digits);
        if (digits.length() != 9) {
            throw new IllegalArgumentException("First 9 digits required, got: " + digits.length());
        }

        int sum = 0;
        for (int i = 0; i < 8; i++) {
            int d = digits.charAt(i) - '0';
            sum += d * WEIGHTS[i];
        }

        int d9 = digits.charAt(8) - '0';
        int temp = d9 * WEIGHTS[8];
        sum += (temp / 10) + (temp % 10);

        int remainder = sum % 10;
        return (10 - remainder) % 10;
    }

    /**
     * Identifies the business classification (BizType) based on digits 4 and 5 (index 3 and 4).
     */
    public static BizType getBizType(String number) {
        if (number == null) {
            return BizType.UNKNOWN;
        }
        String digits = unformat(number);
        if (digits.length() < 5) {
            return BizType.UNKNOWN;
        }
        return BizType.fromCode(digits.substring(3, 5));
    }

    /**
     * Formats 10 digits into "XXX-XX-XXXXX".
     */
    public static String format(String rawNumber) {
        if (rawNumber == null) {
            return null;
        }
        String digits = unformat(rawNumber);
        if (digits.length() != 10) {
            return rawNumber;
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5, 10);
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
     * Checks if the format strictly conforms to "XXX-XX-XXXXX".
     */
    public static boolean hasValidPattern(String number) {
        return number != null && FORMATTED_PATTERN.matcher(number).matches();
    }

    private static boolean verifyChecksum(String digits) {
        try {
            int expected = calculateCheckDigit(digits.substring(0, 9));
            int actual = digits.charAt(9) - '0';
            return expected == actual;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
