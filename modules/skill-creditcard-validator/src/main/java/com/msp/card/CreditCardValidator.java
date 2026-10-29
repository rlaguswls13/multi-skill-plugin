package com.msp.card;

/**
 * Validates credit card numbers using Luhn checksum and brand prefix/length rules.
 */
public final class CreditCardValidator {

    private CreditCardValidator() {
    }

    /**
     * Checks if the card number has valid length and passes the Luhn checksum.
     */
    public static boolean isValid(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return false;
        }
        return LuhnChecksum.validate(cardNumber);
    }

    /**
     * Detects brand issuer from card number.
     */
    public static CardBrand detectBrand(String cardNumber) {
        return CardBrand.detect(cardNumber);
    }

    /**
     * Full validation checking both Luhn algorithm and brand issuer compatibility.
     */
    public static CardValidationResult validate(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return CardValidationResult.invalid(cardNumber, "Card number is empty or null");
        }
        String clean = cardNumber.replaceAll("[^0-9]", "");
        if (clean.length() < 12 || clean.length() > 19) {
            return CardValidationResult.invalid(cardNumber, "Card number length must be between 12 and 19 digits");
        }

        if (!LuhnChecksum.validate(clean)) {
            return CardValidationResult.invalid(cardNumber, "Invalid Luhn checksum");
        }

        CardBrand brand = CardBrand.detect(clean);
        return CardValidationResult.valid(brand, clean);
    }
}
