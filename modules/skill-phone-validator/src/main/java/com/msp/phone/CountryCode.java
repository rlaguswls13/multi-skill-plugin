package com.msp.phone;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Standard country dialing codes for phone number validation and E.164 normalization.
 */
public enum CountryCode {
    KR("+82", "South Korea"),
    US("+1", "United States"),
    CA("+1", "Canada"),
    JP("+81", "Japan"),
    CN("+86", "China"),
    GB("+44", "United Kingdom"),
    DE("+49", "Germany"),
    FR("+33", "France"),
    AU("+61", "Australia"),
    SG("+65", "Singapore"),
    VN("+84", "Vietnam"),
    UNKNOWN("", "Unknown");

    private final String dialCode;
    private final String countryName;

    CountryCode(String dialCode, String countryName) {
        this.dialCode = dialCode;
        this.countryName = countryName;
    }

    public String getDialCode() {
        return dialCode;
    }

    public String getCountryName() {
        return countryName;
    }

    /**
     * Detects CountryCode from a phone number string that may start with '+' and dialing code.
     */
    public static CountryCode detect(String phone) {
        if (phone == null || phone.isBlank()) {
            return UNKNOWN;
        }
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (!cleaned.startsWith("+")) {
            return UNKNOWN;
        }

        // Match longest dialing codes first
        return Arrays.stream(values())
                .filter(c -> !c.dialCode.isEmpty())
                .filter(c -> cleaned.startsWith(c.dialCode))
                .max(Comparator.comparingInt(c -> c.dialCode.length()))
                .orElse(UNKNOWN);
    }
}
