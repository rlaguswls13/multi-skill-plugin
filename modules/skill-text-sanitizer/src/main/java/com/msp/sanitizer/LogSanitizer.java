package com.msp.sanitizer;

import java.util.regex.Pattern;

/**
 * Thread-safe utility for neutralizing Log Injection / Log Forgery attacks (CWE-117)
 * by eliminating or escaping Carriage Returns (CR), Line Feeds (LF), and malicious control characters.
 */
public final class LogSanitizer {

    private static final String DEFAULT_REPLACEMENT = "_";

    // Matches CR, LF, and Unicode line separators (\u2028, \u2029, \u0085)
    private static final Pattern CRLF_PATTERN = Pattern.compile("(\r\n|\r|\n|\u2028|\u2029|\u0085)");

    // Matches unprintable ASCII and ISO control characters (excluding standard whitespace like space and tab)
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\t ]]");

    private LogSanitizer() {
    }

    /**
     * Sanitizes a log message by replacing all CRLF sequences with "_"
     * and stripping non-printable control characters.
     *
     * @param message raw log message
     * @return sanitized single-line log message
     */
    public static String sanitize(String message) {
        return sanitize(message, DEFAULT_REPLACEMENT);
    }

    /**
     * Sanitizes a log message by replacing all CRLF sequences with the specified replacement string
     * and stripping unprintable control characters.
     *
     * @param message     raw log message
     * @param replacement replacement string for newlines (e.g. " ", "_", "")
     * @return sanitized single-line log message
     */
    public static String sanitize(String message, String replacement) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String rep = (replacement == null) ? "" : replacement;
        String noCrlf = CRLF_PATTERN.matcher(message).replaceAll(rep);
        return CONTROL_CHARS.matcher(noCrlf).replaceAll("");
    }

    /**
     * Escapes CRLF sequences into visible literal representations ("\\r", "\\n", "\\t").
     *
     * @param message raw log message
     * @return escaped log message
     */
    public static String escape(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        StringBuilder sb = new StringBuilder(message.length() + 16);
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            switch (c) {
                case '\r' -> sb.append("\\r");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
                        // ignore or escape other control chars
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Checks if a log message contains CRLF or newline characters.
     *
     * @param message log message
     * @return true if newline characters exist
     */
    public static boolean containsCrlf(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        return CRLF_PATTERN.matcher(message).find();
    }
}
