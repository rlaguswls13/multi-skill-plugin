package com.msp.card;

/**
 * Result of credit card validation check.
 *
 * @param isValid      whether both length, format, and Luhn checksum passed
 * @param brand        the detected card brand
 * @param cleanNumber  cleaned pure digits
 * @param errorMessage error description if invalid, or null if valid
 */
public record CardValidationResult(
        boolean isValid,
        CardBrand brand,
        String cleanNumber,
        String errorMessage
) {
    public static CardValidationResult valid(CardBrand brand, String cleanNumber) {
        return new CardValidationResult(true, brand, cleanNumber, null);
    }

    public static CardValidationResult invalid(String raw, String errorMessage) {
        String clean = raw != null ? raw.replaceAll("[^0-9]", "") : "";
        return new CardValidationResult(false, CardBrand.UNKNOWN, clean, errorMessage);
    }
}
