package com.msp.sanitizer;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe utility for detecting and masking Personally Identifiable Information (PII)
 * including Korean Resident Registration Numbers (RRN / 주민등록번호),
 * Credit Card numbers, Phone numbers, and Email addresses.
 */
public final class PiiMasker {

    private static final char DEFAULT_MASK_CHAR = '*';

    // RRN: 6 digits - 7 digits (gender digit is 1-8 or 0, 9)
    private static final Pattern RRN_PATTERN = Pattern.compile(
            "\\b(\\d{6})([- ]?)([0-9])(\\d{6})\\b"
    );

    // Credit Card: 4 groups of 4 digits (hyphen or space separated)
    private static final Pattern CARD_FORMATTED_PATTERN = Pattern.compile(
            "\\b(\\d{4})([- ]?)(\\d{4})([- ]?)(\\d{4})([- ]?)(\\d{4})\\b"
    );

    // Credit Card: 16 consecutive digits
    private static final Pattern CARD_PLAIN_PATTERN = Pattern.compile(
            "\\b(\\d{4})(\\d{8})(\\d{4})\\b"
    );

    // Phone: Korean Mobile (010, 011...), Area codes (02, 031...), VoIP (070), Safe numbers (050X)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(01[016789]|02|0[3-6][1-5]|070|050\\d)([- ]?)(\\d{3,4})([- ]?)(\\d{4})\\b"
    );

    // Email: standard email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b"
    );

    private PiiMasker() {
    }

    /**
     * Masks Korean Resident Registration Number (RRN).
     * By default preserves the birth date (first 6 digits) and the gender digit, masking the last 6 digits.
     * Example: "950101-1234567" -> "950101-1******"
     *
     * @param text input text
     * @return masked text
     */
    public static String maskRrn(String text) {
        return maskRrn(text, DEFAULT_MASK_CHAR, true);
    }

    /**
     * Masks Korean Resident Registration Number (RRN) with customizable options.
     *
     * @param text            input text
     * @param maskChar        character used for masking
     * @param keepGenderDigit whether to preserve the 7th digit (gender/century)
     * @return masked text
     */
    public static String maskRrn(String text, char maskChar, boolean keepGenderDigit) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = RRN_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (matcher.find()) {
            String front = matcher.group(1);
            String sep = matcher.group(2);
            String gender = matcher.group(3);
            if (keepGenderDigit) {
                String replacement = front + sep + gender + String.valueOf(maskChar).repeat(6);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                String replacement = front + sep + String.valueOf(maskChar).repeat(7);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks 16-digit credit card numbers.
     * Example: "1234-5678-9012-3456" -> "1234-****-****-3456"
     * Example: "1234567890123456" -> "1234********3456"
     *
     * @param text input text
     * @return masked text
     */
    public static String maskCreditCard(String text) {
        return maskCreditCard(text, DEFAULT_MASK_CHAR);
    }

    /**
     * Masks credit card numbers with a custom mask character.
     *
     * @param text     input text
     * @param maskChar character used for masking
     * @return masked text
     */
    public static String maskCreditCard(String text, char maskChar) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Mask formatted cards: 1234-5678-9012-3456 -> 1234-****-****-3456
        Matcher formattedMatcher = CARD_FORMATTED_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        String mask4 = String.valueOf(maskChar).repeat(4);
        while (formattedMatcher.find()) {
            String g1 = formattedMatcher.group(1);
            String s1 = formattedMatcher.group(2);
            String s2 = formattedMatcher.group(4);
            String s3 = formattedMatcher.group(6);
            String g4 = formattedMatcher.group(7);
            String replacement = g1 + s1 + mask4 + s2 + mask4 + s3 + g4;
            formattedMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        formattedMatcher.appendTail(sb);

        // Mask plain 16-digit cards: 1234567890123456 -> 1234********3456
        Matcher plainMatcher = CARD_PLAIN_PATTERN.matcher(sb.toString());
        StringBuilder sb2 = new StringBuilder(sb.length());
        String mask8 = String.valueOf(maskChar).repeat(8);
        while (plainMatcher.find()) {
            String g1 = plainMatcher.group(1);
            String g3 = plainMatcher.group(3);
            String replacement = g1 + mask8 + g3;
            plainMatcher.appendReplacement(sb2, Matcher.quoteReplacement(replacement));
        }
        plainMatcher.appendTail(sb2);

        return sb2.toString();
    }

    /**
     * Masks Korean phone and mobile numbers (masks the middle 3 or 4 digits).
     * Example: "010-1234-5678" -> "010-****-5678"
     * Example: "02-123-4567" -> "02-***-4567"
     * Example: "01012345678" -> "010****5678"
     *
     * @param text input text
     * @return masked text
     */
    public static String maskPhone(String text) {
        return maskPhone(text, DEFAULT_MASK_CHAR);
    }

    /**
     * Masks phone numbers with a custom mask character.
     *
     * @param text     input text
     * @param maskChar character used for masking
     * @return masked text
     */
    public static String maskPhone(String text, char maskChar) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = PHONE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String sep1 = matcher.group(2);
            String mid = matcher.group(3);
            String sep2 = matcher.group(4);
            String suffix = matcher.group(5);

            String maskedMid = String.valueOf(maskChar).repeat(mid.length());
            String replacement = prefix + sep1 + maskedMid + sep2 + suffix;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks email addresses.
     * Preserves up to first 2 characters of local part and masks the remainder.
     * Example: "john.doe@example.com" -> "jo******@example.com"
     * Example: "me@example.com" -> "m*@example.com"
     * Example: "a@example.com" -> "*@example.com"
     *
     * @param text input text
     * @return masked text
     */
    public static String maskEmail(String text) {
        return maskEmail(text, DEFAULT_MASK_CHAR, 2);
    }

    /**
     * Masks email addresses with custom mask character and visible prefix count.
     *
     * @param text          input text
     * @param maskChar       mask character
     * @param visiblePrefix number of visible characters in username/local part
     * @return masked text
     */
    public static String maskEmail(String text, char maskChar, int visiblePrefix) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = EMAIL_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (matcher.find()) {
            String local = matcher.group(1);
            String domain = matcher.group(2);

            String maskedLocal;
            if (local.length() <= 1) {
                maskedLocal = String.valueOf(maskChar);
            } else if (local.length() <= visiblePrefix) {
                maskedLocal = local.charAt(0) + String.valueOf(maskChar).repeat(local.length() - 1);
            } else {
                int prefixLen = Math.max(1, visiblePrefix);
                String prefix = local.substring(0, prefixLen);
                maskedLocal = prefix + String.valueOf(maskChar).repeat(local.length() - prefixLen);
            }

            String replacement = maskedLocal + "@" + domain;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks all detected PII (Email, Credit Card, RRN, Phone) in the given text.
     *
     * @param text input text
     * @return masked text
     */
    public static String maskAll(String text) {
        return maskAll(text, DEFAULT_MASK_CHAR);
    }

    /**
     * Masks all detected PII with a custom mask character.
     *
     * @param text     input text
     * @param maskChar character used for masking
     * @return masked text
     */
    public static String maskAll(String text, char maskChar) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = maskEmail(text, maskChar, 2);
        result = maskCreditCard(result, maskChar);
        result = maskRrn(result, maskChar, true);
        result = maskPhone(result, maskChar);
        return result;
    }
}
