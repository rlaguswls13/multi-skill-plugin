package com.msp.linkheader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * RFC 8187 {@code ext-value} representation ({@code charset'language'value-chars}),
 * used for extended Link parameters such as {@code title*}.
 *
 * @param charset  IANA character set name (e.g. {@code UTF-8})
 * @param language optional BCP 47 language tag (empty string if omitted)
 * @param value    decoded Unicode string value
 */
public record ExtValue(String charset, String language, String value) {

    public ExtValue {
        Objects.requireNonNull(charset, "charset must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (charset.isBlank()) {
            throw new IllegalArgumentException("charset must not be blank");
        }
    }

    /**
     * Creates a UTF-8 {@code ExtValue} with the given language tag and text value.
     */
    public static ExtValue utf8(String language, String value) {
        return new ExtValue("UTF-8", language == null ? "" : language, value);
    }

    /**
     * Parses an RFC 8187 {@code ext-value} string (e.g. {@code UTF-8'en'Next%20Page}).
     */
    public static ExtValue parse(String raw) {
        if (raw == null) {
            throw new LinkHeaderException("ext-value must not be null");
        }
        int firstQuote = raw.indexOf('\'');
        if (firstQuote <= 0) {
            throw new LinkHeaderException("Invalid RFC 8187 ext-value (missing charset delimiter): " + raw);
        }
        int secondQuote = raw.indexOf('\'', firstQuote + 1);
        if (secondQuote < 0) {
            throw new LinkHeaderException("Invalid RFC 8187 ext-value (missing language delimiter): " + raw);
        }

        String charsetName = raw.substring(0, firstQuote).trim();
        String language = raw.substring(firstQuote + 1, secondQuote).trim();
        String encodedValue = raw.substring(secondQuote + 1);

        Charset cs;
        try {
            cs = Charset.forName(charsetName);
        } catch (IllegalArgumentException e) {
            throw new LinkHeaderException("Unsupported charset in ext-value: " + charsetName);
        }

        byte[] decodedBytes = percentDecode(encodedValue);
        String decoded = new String(decodedBytes, cs);
        return new ExtValue(charsetName.toUpperCase(Locale.ROOT), language, decoded);
    }

    /**
     * Encodes this {@code ExtValue} into RFC 8187 {@code charset'language'value-chars} syntax.
     */
    public String encode() {
        Charset cs;
        try {
            cs = Charset.forName(charset);
        } catch (IllegalArgumentException e) {
            cs = StandardCharsets.UTF_8;
        }
        byte[] bytes = value.getBytes(cs);
        StringBuilder sb = new StringBuilder(charset.length() + language.length() + bytes.length * 3 + 2);
        sb.append(charset).append('\'').append(language).append('\'');
        for (byte b : bytes) {
            int unsigned = b & 0xFF;
            if (isAttrChar(unsigned)) {
                sb.append((char) unsigned);
            } else {
                sb.append('%');
                sb.append(HEX[(unsigned >>> 4) & 0x0F]);
                sb.append(HEX[unsigned & 0x0F]);
            }
        }
        return sb.toString();
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    // RFC 8187 Section 3.2.1 attr-char
    private static boolean isAttrChar(int c) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
            return true;
        }
        return switch (c) {
            case '!', '#', '$', '&', '+', '-', '.', '^', '_', '`', '|', '~' -> true;
            default -> false;
        };
    }

    private static byte[] percentDecode(String input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '%') {
                if (i + 2 >= input.length()) {
                    throw new LinkHeaderException("Truncated percent-encoding in ext-value", i);
                }
                int hi = Character.digit(input.charAt(i + 1), 16);
                int lo = Character.digit(input.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    throw new LinkHeaderException("Invalid hex escape in ext-value", i);
                }
                out.write((hi << 4) | lo);
                i += 2;
            } else {
                out.write((byte) c);
            }
        }
        return out.toByteArray();
    }
}
