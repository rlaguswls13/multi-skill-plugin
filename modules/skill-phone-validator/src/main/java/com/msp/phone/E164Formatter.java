package com.msp.phone;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Utility for phone number formatting, sanitization, and E.164 normalization.
 */
public final class E164Formatter {

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private E164Formatter() {
    }

    /**
     * Cleans phone string, keeping digits and leading '+' if present.
     */
    public static String clean(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String trimmed = phone.trim();
        boolean startsWithPlus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");
        return startsWithPlus ? "+" + digits : digits;
    }

    /**
     * Extracts pure numeric digits from the input string.
     */
    public static String digitsOnly(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        return phone.replaceAll("[^0-9]", "");
    }

    /**
     * Normalizes a phone number to international E.164 format (+[countryCode][nationalNumber]).
     */
    public static String toE164(String phone, CountryCode defaultCountry) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String cleaned = clean(phone);
        if (cleaned.startsWith("+")) {
            return E164_PATTERN.matcher(cleaned).matches() ? cleaned : "";
        }

        CountryCode country = defaultCountry != null ? defaultCountry : CountryCode.KR;
        String digits = digitsOnly(cleaned);
        if (digits.isEmpty()) {
            return "";
        }

        if (country == CountryCode.KR) {
            if (digits.startsWith("82")) {
                String e164 = "+" + digits;
                return E164_PATTERN.matcher(e164).matches() ? e164 : "";
            }
            if (digits.startsWith("0")) {
                String e164 = "+82" + digits.substring(1);
                return E164_PATTERN.matcher(e164).matches() ? e164 : "";
            }
            // Representative or numbers without leading 0
            String e164 = "+82" + digits;
            return E164_PATTERN.matcher(e164).matches() ? e164 : "";
        }

        if (country == CountryCode.US || country == CountryCode.CA) {
            if (digits.length() == 10) {
                return "+1" + digits;
            }
            if (digits.length() == 11 && digits.startsWith("1")) {
                return "+" + digits;
            }
        }

        if (country != CountryCode.UNKNOWN && !country.getDialCode().isEmpty()) {
            String dialCode = country.getDialCode();
            String national = digits.startsWith("0") ? digits.substring(1) : digits;
            String e164 = dialCode + national;
            return E164_PATTERN.matcher(e164).matches() ? e164 : "";
        }

        return "";
    }

    /**
     * Formats Korean phone numbers into standard dashed representation.
     * Supports Mobile (010), Landline (02, 031~064), Internet (070), Toll-free (080), and Representative (1588).
     */
    public static String formatKorean(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = digitsOnly(phone);
        if (digits.isEmpty()) {
            return "";
        }

        // Handle international +82 prefix
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }

        // Representative 8-digit numbers (1588, 1577, 1544, etc.)
        if (digits.length() == 8 && (digits.startsWith("15") || digits.startsWith("16") || digits.startsWith("18"))) {
            return digits.substring(0, 4) + "-" + digits.substring(4);
        }

        // Seoul area code 02
        if (digits.startsWith("02")) {
            if (digits.length() == 9) { // 02-xxx-xxxx
                return digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5);
            } else if (digits.length() == 10) { // 02-xxxx-xxxx
                return digits.substring(0, 2) + "-" + digits.substring(2, 6) + "-" + digits.substring(6);
            }
        }

        // Virtual numbers 0505, 0504 etc. (12 digits)
        if (digits.startsWith("050") && digits.length() == 12) {
            return digits.substring(0, 4) + "-" + digits.substring(4, 8) + "-" + digits.substring(8);
        }

        // Standard 3-digit prefixes (010, 011, 031, 070, 080, etc.)
        if (digits.length() == 10) { // 0xx-xxx-xxxx
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        } else if (digits.length() == 11) { // 0xx-xxxx-xxxx
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }

        // If unknown structure, return cleaned digits
        return digits;
    }
}
