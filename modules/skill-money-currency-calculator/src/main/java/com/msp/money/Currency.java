package com.msp.money;

public enum Currency {
    KRW(0, "₩"),
    USD(2, "$"),
    EUR(2, "€"),
    JPY(0, "¥"),
    GBP(2, "£"),
    CNY(2, "¥");

    private final int defaultFractionDigits;
    private final String symbol;

    Currency(int defaultFractionDigits, String symbol) {
        this.defaultFractionDigits = defaultFractionDigits;
        this.symbol = symbol;
    }

    public int defaultFractionDigits() {
        return defaultFractionDigits;
    }

    public String symbol() {
        return symbol;
    }
}
