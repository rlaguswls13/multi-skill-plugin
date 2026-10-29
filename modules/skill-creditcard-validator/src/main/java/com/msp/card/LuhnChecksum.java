package com.msp.card;

/**
 * Standard ISO/IEC 7812 Luhn (Mod 10) algorithm validator and check digit generator.
 */
public final class LuhnChecksum {

    private LuhnChecksum() {
    }

    /**
     * Verifies if the card number satisfies the Luhn checksum.
     *
     * @param cardNumber card number string (can contain spaces or dashes)
     * @return true if valid Mod 10 checksum, false otherwise
     */
    public static boolean validate(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return false;
        }
        String digits = cardNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 2) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    /**
     * Calculates the Luhn check digit (0-9) to append to the end of a digit payload.
     *
     * @param payload card number digits without the final check digit
     * @return check digit (0 to 9)
     */
    public static int calculateCheckDigit(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be null or blank");
        }
        String digits = payload.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("Payload must contain digits");
        }

        int sum = 0;
        boolean alternate = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        int mod = sum % 10;
        return (mod == 0) ? 0 : 10 - mod;
    }
}
