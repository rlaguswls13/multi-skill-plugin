package com.msp.jsonmasker;

/**
 * Masking strategy functional interface defining how sensitive string/number values are redacted.
 */
@FunctionalInterface
public interface MaskingStrategy {

    /**
     * Masks the raw input value according to the strategy rules.
     *
     * @param rawValue the original raw value string
     * @return the masked / redacted string
     */
    String mask(String rawValue);

    /**
     * Standard full masking strategy replacing entire value with "***".
     */
    MaskingStrategy FULL = raw -> "***";

    /**
     * Credit card partial masking strategy:
     * Retains first 4 and last 4 digits while masking the middle with "****-****-".
     * E.g. "4111-2222-3333-1111" -> "4111-****-****-1111", "4111222233331111" -> "4111-****-****-1111".
     */
    MaskingStrategy PARTIAL_CARD = raw -> {
        if (raw == null || raw.isBlank()) {
            return "***";
        }
        String clean = raw.trim();
        String digits = clean.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            return "***";
        }
        if (clean.contains("-")) {
            String[] parts = clean.split("-");
            if (parts.length == 4) {
                return parts[0] + "-****-****-" + parts[3];
            }
        }
        String first4 = digits.substring(0, 4);
        String last4 = digits.substring(digits.length() - 4);
        if (digits.length() == 16) {
            return first4 + "-****-****-" + last4;
        }
        return first4 + "*".repeat(Math.max(4, digits.length() - 8)) + last4;
    };

    /**
     * Email partial masking strategy:
     * Retains first character of local part, masks rest of local part with "***", retains domain.
     * E.g. "john.doe@example.com" -> "j***@example.com".
     */
    MaskingStrategy PARTIAL_EMAIL = raw -> {
        if (raw == null || raw.isBlank()) {
            return "***";
        }
        String clean = raw.trim();
        int atIdx = clean.indexOf('@');
        if (atIdx <= 0 || atIdx == clean.length() - 1) {
            return "***";
        }
        String local = clean.substring(0, atIdx);
        String domain = clean.substring(atIdx);
        return local.charAt(0) + "***" + domain;
    };

    /**
     * Factory for custom fixed replacement masking.
     *
     * @param replacement replacement string (e.g. "[REDACTED]" or "###")
     * @return custom MaskingStrategy instance
     */
    static MaskingStrategy custom(String replacement) {
        String fixed = replacement == null ? "***" : replacement;
        return raw -> fixed;
    }
}
