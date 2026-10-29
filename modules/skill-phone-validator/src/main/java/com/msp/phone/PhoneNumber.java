package com.msp.phone;

/**
 * Immutable representation of a parsed and validated phone number.
 *
 * @param raw         the original input string
 * @param cleanDigits cleaned pure digits (without country prefix if local, or with dial code)
 * @param e164        normalized E.164 string (e.g. "+821012345678")
 * @param country     detected or default country code
 * @param type        phone type classification (MOBILE, LANDLINE, etc.)
 * @param formatted   human-readable formatted representation (e.g. "010-1234-5678")
 * @param isValid     whether the phone number is valid according to format rules
 */
public record PhoneNumber(
        String raw,
        String cleanDigits,
        String e164,
        CountryCode country,
        PhoneType type,
        String formatted,
        boolean isValid
) {
    public static PhoneNumber invalid(String raw) {
        return new PhoneNumber(raw, "", "", CountryCode.UNKNOWN, PhoneType.UNKNOWN, "", false);
    }
}
