package com.msp.card;

/**
 * Utility for credit card number masking and grouped dashed formatting to ensure PCI-DSS compliance.
 */
public final class CardMasker {

    private CardMasker() {
    }

    /**
     * Masks middle digits with '*', keeping the first 4 and last 4 digits visible.
     * E.g. "4111222233334444" -> "4111********4444".
     */
    public static String mask(String cardNumber) {
        return mask(cardNumber, 4, 4, '*');
    }

    /**
     * Custom masking keeping specified count of visible start and end digits.
     */
    public static String mask(String cardNumber, int startVisible, int endVisible, char maskChar) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return "";
        }
        String digits = cardNumber.replaceAll("[^0-9]", "");
        int len = digits.length();
        if (len <= startVisible + endVisible) {
            return String.valueOf(maskChar).repeat(len);
        }

        StringBuilder sb = new StringBuilder(len);
        sb.append(digits, 0, startVisible);
        for (int i = startVisible; i < len - endVisible; i++) {
            sb.append(maskChar);
        }
        sb.append(digits.substring(len - endVisible));
        return sb.toString();
    }

    /**
     * Formats card digits into dashed format (e.g. 4-4-4-4 for Visa/Mastercard or 4-6-5 for AMEX).
     */
    public static String formatDashed(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return "";
        }
        String digits = cardNumber.replaceAll("[^0-9]", "");
        int len = digits.length();

        // AMEX 15 digits: 4-6-5
        if (len == 15) {
            return digits.substring(0, 4) + "-" + digits.substring(4, 10) + "-" + digits.substring(10);
        }
        // Diners 14 digits: 4-6-4
        if (len == 14) {
            return digits.substring(0, 4) + "-" + digits.substring(4, 10) + "-" + digits.substring(10);
        }
        // Standard 16 digits: 4-4-4-4
        if (len == 16) {
            return digits.substring(0, 4) + "-" + digits.substring(4, 8) + "-" + digits.substring(8, 12) + "-" + digits.substring(12);
        }

        // Default 4-digit chunks
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i += 4) {
            if (i > 0) sb.append("-");
            sb.append(digits, i, Math.min(i + 4, len));
        }
        return sb.toString();
    }

    /**
     * Masks card number in standard dashed representation: "4111-****-****-4444".
     */
    public static String maskDashed(String cardNumber) {
        String dashed = formatDashed(cardNumber);
        if (dashed.isEmpty()) {
            return "";
        }
        String[] parts = dashed.split("-");
        if (parts.length <= 2) {
            return mask(cardNumber);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("-");
            if (i == 0 || i == parts.length - 1) {
                sb.append(parts[i]);
            } else {
                sb.append("*".repeat(parts[i].length()));
            }
        }
        return sb.toString();
    }
}
