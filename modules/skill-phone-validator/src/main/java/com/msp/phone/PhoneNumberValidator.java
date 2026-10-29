package com.msp.phone;

import java.util.regex.Pattern;

/**
 * Validates and parses phone numbers (Korean mobile/landline/representative and international E.164 formats).
 */
public final class PhoneNumberValidator {

    private static final Pattern KOREAN_MOBILE_PATTERN = Pattern.compile("^01[016789]\\d{7,8}$");
    private static final Pattern KOREAN_SEOUL_PATTERN = Pattern.compile("^02\\d{7,8}$");
    private static final Pattern KOREAN_REGIONAL_PATTERN = Pattern.compile("^0(3[1-3]|4[1-4]|5[1-5]|6[1-4])\\d{7,8}$");
    private static final Pattern KOREAN_VOIP_PATTERN = Pattern.compile("^070\\d{7,8}$");
    private static final Pattern KOREAN_TOLLFREE_PATTERN = Pattern.compile("^080\\d{6,7}$");
    private static final Pattern KOREAN_VIRTUAL_PATTERN = Pattern.compile("^050[2-8]\\d{7,8}$");
    private static final Pattern KOREAN_REPRESENTATIVE_PATTERN = Pattern.compile("^(15|16|18)\\d{6}$");
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private PhoneNumberValidator() {
    }

    /**
     * Checks if a phone number is valid as either a Korean number or standard E.164 international format.
     */
    public static boolean isValid(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return isValidKorean(phone) || isValidE164(phone);
    }

    /**
     * Checks if a phone number conforms to any Korean phone number standard.
     */
    public static boolean isValidKorean(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        String digits = E164Formatter.digitsOnly(phone);
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        return KOREAN_MOBILE_PATTERN.matcher(digits).matches()
                || KOREAN_SEOUL_PATTERN.matcher(digits).matches()
                || KOREAN_REGIONAL_PATTERN.matcher(digits).matches()
                || KOREAN_VOIP_PATTERN.matcher(digits).matches()
                || KOREAN_TOLLFREE_PATTERN.matcher(digits).matches()
                || KOREAN_VIRTUAL_PATTERN.matcher(digits).matches()
                || KOREAN_REPRESENTATIVE_PATTERN.matcher(digits).matches();
    }

    /**
     * Checks specifically if a phone number is a Korean mobile number (010, 011, etc.).
     */
    public static boolean isValidKoreanMobile(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        String digits = E164Formatter.digitsOnly(phone);
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        return KOREAN_MOBILE_PATTERN.matcher(digits).matches();
    }

    /**
     * Checks if the phone number is a valid international E.164 format (e.g. +821012345678, +14155552671).
     */
    public static boolean isValidE164(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        String cleaned = E164Formatter.clean(phone);
        return E164_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Detects CountryCode from dialing code or fallback.
     */
    public static CountryCode detectCountry(String phone) {
        CountryCode detected = CountryCode.detect(phone);
        if (detected != CountryCode.UNKNOWN) {
            return detected;
        }
        if (isValidKorean(phone)) {
            return CountryCode.KR;
        }
        return CountryCode.UNKNOWN;
    }

    /**
     * Identifies the category of phone service.
     */
    public static PhoneType detectType(String phone) {
        if (phone == null || phone.isBlank()) {
            return PhoneType.UNKNOWN;
        }
        String digits = E164Formatter.digitsOnly(phone);
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }

        if (KOREAN_MOBILE_PATTERN.matcher(digits).matches()) {
            return PhoneType.MOBILE;
        }
        if (KOREAN_SEOUL_PATTERN.matcher(digits).matches() || KOREAN_REGIONAL_PATTERN.matcher(digits).matches()) {
            return PhoneType.LANDLINE;
        }
        if (KOREAN_VOIP_PATTERN.matcher(digits).matches()) {
            return PhoneType.VOIP;
        }
        if (KOREAN_TOLLFREE_PATTERN.matcher(digits).matches()) {
            return PhoneType.TOLL_FREE;
        }
        if (KOREAN_REPRESENTATIVE_PATTERN.matcher(digits).matches()) {
            return PhoneType.REPRESENTATIVE;
        }

        if (isValidE164(phone)) {
            return PhoneType.INTERNATIONAL;
        }
        return PhoneType.UNKNOWN;
    }

    /**
     * Full parse of a phone number string into a rich {@link PhoneNumber} record.
     */
    public static PhoneNumber parse(String phone, CountryCode defaultCountry) {
        if (phone == null || phone.isBlank()) {
            return PhoneNumber.invalid(phone);
        }

        boolean valid = isValid(phone);
        CountryCode country = detectCountry(phone);
        if (country == CountryCode.UNKNOWN && defaultCountry != null) {
            country = defaultCountry;
        }

        PhoneType type = detectType(phone);
        String e164 = E164Formatter.toE164(phone, country);
        String formatted = (country == CountryCode.KR)
                ? E164Formatter.formatKorean(phone)
                : (e164.isEmpty() ? E164Formatter.clean(phone) : e164);

        return new PhoneNumber(
                phone,
                E164Formatter.digitsOnly(phone),
                e164,
                country,
                type,
                formatted,
                valid
        );
    }
}
