package com.msp.sanitizer;

/**
 * Unified facade for text sanitization, PII masking, HTML/XSS neutralization,
 * and Log injection prevention.
 */
public final class TextSanitizers {

    private TextSanitizers() {
    }

    // --- PII Masking ---

    public static String maskRrn(String text) {
        return PiiMasker.maskRrn(text);
    }

    public static String maskRrn(String text, char maskChar, boolean keepGenderDigit) {
        return PiiMasker.maskRrn(text, maskChar, keepGenderDigit);
    }

    public static String maskCreditCard(String text) {
        return PiiMasker.maskCreditCard(text);
    }

    public static String maskCreditCard(String text, char maskChar) {
        return PiiMasker.maskCreditCard(text, maskChar);
    }

    public static String maskPhone(String text) {
        return PiiMasker.maskPhone(text);
    }

    public static String maskPhone(String text, char maskChar) {
        return PiiMasker.maskPhone(text, maskChar);
    }

    public static String maskEmail(String text) {
        return PiiMasker.maskEmail(text);
    }

    public static String maskEmail(String text, char maskChar, int visiblePrefix) {
        return PiiMasker.maskEmail(text, maskChar, visiblePrefix);
    }

    public static String maskAllPii(String text) {
        return PiiMasker.maskAll(text);
    }

    public static String maskAllPii(String text, char maskChar) {
        return PiiMasker.maskAll(text, maskChar);
    }

    // --- HTML / XSS Sanitization ---

    public static String escapeHtml(String html) {
        return HtmlSanitizer.escapeHtml(html);
    }

    public static String unescapeHtml(String html) {
        return HtmlSanitizer.unescapeHtml(html);
    }

    public static String stripHtml(String html) {
        return HtmlSanitizer.stripTags(html);
    }

    public static String sanitizeHtml(String html) {
        return HtmlSanitizer.sanitize(html);
    }

    public static boolean containsXss(String html) {
        return HtmlSanitizer.containsXss(html);
    }

    // --- Log Injection Neutralization ---

    public static String sanitizeLog(String log) {
        return LogSanitizer.sanitize(log);
    }

    public static String sanitizeLog(String log, String replacement) {
        return LogSanitizer.sanitize(log, replacement);
    }

    public static String escapeLog(String log) {
        return LogSanitizer.escape(log);
    }

    public static boolean containsCrlf(String log) {
        return LogSanitizer.containsCrlf(log);
    }
}
