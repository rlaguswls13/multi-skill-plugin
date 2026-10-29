package com.msp.ssn;

/**
 * Korean Resident Registration Number masking utility.
 */
public final class SsnMasker {

    private SsnMasker() {
    }

    /**
     * Standard masking: preserves the birth date and gender digit.
     * Example: "900101-1234567" -> "900101-1******"
     * Example: "9001011234567"  -> "900101-1******"
     */
    public static String mask(String ssn) {
        if (ssn == null) {
            return null;
        }
        String digits = SsnValidator.unformat(ssn);
        if (digits.length() != 13) {
            return ssn;
        }
        return digits.substring(0, 6) + "-" + digits.charAt(6) + "******";
    }

    /**
     * Full back-part masking: masks all 7 digits of the second part.
     * Example: "900101-1234567" -> "900101-*******"
     */
    public static String maskFull(String ssn) {
        if (ssn == null) {
            return null;
        }
        String digits = SsnValidator.unformat(ssn);
        if (digits.length() != 13) {
            return ssn;
        }
        return digits.substring(0, 6) + "-*******";
    }

    /**
     * Masks the front birth date part.
     * Example: "900101-1234567" -> "******-1234567"
     */
    public static String maskBirthOnly(String ssn) {
        if (ssn == null) {
            return null;
        }
        String digits = SsnValidator.unformat(ssn);
        if (digits.length() != 13) {
            return ssn;
        }
        return "******-" + digits.substring(6, 13);
    }

    /**
     * Custom masking by keeping visible prefix and suffix digits.
     */
    public static String maskCustom(String ssn, int keepPrefix, int keepSuffix, char maskChar) {
        if (ssn == null) {
            return null;
        }
        String digits = SsnValidator.unformat(ssn);
        if (digits.length() != 13) {
            return ssn;
        }
        if (keepPrefix < 0 || keepSuffix < 0 || (keepPrefix + keepSuffix) > 13) {
            throw new IllegalArgumentException("Invalid keep bounds: prefix=" + keepPrefix + ", suffix=" + keepSuffix);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(digits, 0, keepPrefix);
        int maskedCount = 13 - keepPrefix - keepSuffix;
        sb.append(String.valueOf(maskChar).repeat(maskedCount));
        if (keepSuffix > 0) {
            sb.append(digits.substring(13 - keepSuffix));
        }

        // Insert hyphen after 6th character
        return sb.substring(0, 6) + "-" + sb.substring(6);
    }
}
