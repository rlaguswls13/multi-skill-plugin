package com.msp.card;

import java.util.regex.Pattern;

/**
 * Recognizes global credit and debit card brands based on IIN/BIN prefix ranges and card lengths.
 */
public enum CardBrand {
    VISA("^4[0-9]{12}(?:[0-9]{3})?(?:[0-9]{3})?$", "Visa"),
    MASTERCARD("^(?:5[1-5][0-9]{14}|2(?:22[1-9]|2[3-9][0-9]|[3-6][0-9]{2}|7[01][0-9]|720)[0-9]{12})$", "Mastercard"),
    AMEX("^3[47][0-9]{13}$", "American Express"),
    JCB("^(?:2131|1800|35\\d{3})\\d{11}$", "JCB"),
    DINERS("^3(?:0[0-5]|[68][0-9])[0-9]{11}$", "Diners Club"),
    DISCOVER("^6(?:011|5[0-9]{2}|4[4-9][0-9]|22(?:1(?:2[6-9]|[3-9][0-9])|[2-8][0-9]{2}|9(?:[01][0-9]|2[0-5])))\\d{12}$", "Discover"),
    UNIONPAY("^(?:62|81)[0-9]{14,17}$", "UnionPay"),
    UNKNOWN("", "Unknown");

    private final Pattern pattern;
    private final String displayName;

    CardBrand(String regex, String displayName) {
        this.pattern = regex.isEmpty() ? null : Pattern.compile(regex);
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Detects brand from cleaned card digits.
     */
    public static CardBrand detect(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return UNKNOWN;
        }
        String digits = cardNumber.replaceAll("[^0-9]", "");
        for (CardBrand brand : values()) {
            if (brand.pattern != null && brand.pattern.matcher(digits).matches()) {
                return brand;
            }
        }

        // Prefix heuristics if full length not yet reached
        if (digits.startsWith("4")) return VISA;
        if (digits.startsWith("34") || digits.startsWith("37")) return AMEX;
        if (digits.startsWith("51") || digits.startsWith("52") || digits.startsWith("53") ||
            digits.startsWith("54") || digits.startsWith("55")) return MASTERCARD;
        if (digits.startsWith("35")) return JCB;
        if (digits.startsWith("62") || digits.startsWith("81")) return UNIONPAY;
        if (digits.startsWith("6011") || digits.startsWith("65")) return DISCOVER;

        return UNKNOWN;
    }
}
