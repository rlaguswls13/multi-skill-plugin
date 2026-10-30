package com.msp.sqlinjection;

public record SqlInjectionDetection(
        boolean detected,
        String ruleName,
        String matchedText
) {
    public static SqlInjectionDetection safe() {
        return new SqlInjectionDetection(false, null, null);
    }

    public static SqlInjectionDetection danger(String ruleName, String matchedText) {
        return new SqlInjectionDetection(true, ruleName, matchedText);
    }
}
