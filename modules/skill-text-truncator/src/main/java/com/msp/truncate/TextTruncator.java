package com.msp.truncate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Safe, zero-dependency text truncator.
 * Supports character length, byte-safe count (UTF-8, EUC-KR), and word count truncation with ellipsis.
 * Guaranteed never to split Unicode surrogate pairs or break multi-byte characters in half.
 */
public final class TextTruncator {

    public static final String DEFAULT_ELLIPSIS = "...";

    private TextTruncator() {
    }

    /**
     * Truncates text by character count with default ellipsis ("...").
     */
    public static String truncateByLength(String text, int maxLength) {
        return truncateByLength(text, maxLength, DEFAULT_ELLIPSIS);
    }

    /**
     * Truncates text by character count with a custom ellipsis.
     *
     * @param text      the input text, may be null
     * @param maxLength maximum total characters including ellipsis
     * @param ellipsis  suffix appended to indicate truncation
     * @return truncated string, or null if input was null
     */
    public static String truncateByLength(String text, int maxLength, String ellipsis) {
        return truncateLengthInternal(text, maxLength, ellipsis, false);
    }

    /**
     * Truncates text by character count while preserving whole words.
     */
    public static String truncateByLengthPreservingWords(String text, int maxLength, String ellipsis) {
        return truncateLengthInternal(text, maxLength, ellipsis, true);
    }

    private static String truncateLengthInternal(String text, int maxLength, String ellipsis, boolean preserveWords) {
        if (text == null) {
            return null;
        }
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must not be negative: " + maxLength);
        }
        if (text.length() <= maxLength) {
            return text;
        }

        String ell = (ellipsis != null) ? ellipsis : "";
        if (ell.length() > maxLength) {
            throw new IllegalArgumentException("maxLength (" + maxLength + ") must be >= ellipsis length (" + ell.length() + ")");
        }

        int budget = maxLength - ell.length();
        if (budget == 0) {
            return ell;
        }

        // Avoid splitting high/low surrogate pairs
        int cutIndex = budget;
        if (Character.isHighSurrogate(text.charAt(cutIndex - 1))) {
            cutIndex--;
        }

        if (preserveWords) {
            int lastSpace = -1;
            for (int i = cutIndex - 1; i >= 0; i--) {
                if (Character.isWhitespace(text.charAt(i))) {
                    lastSpace = i;
                    break;
                }
            }
            if (lastSpace > 0) {
                cutIndex = lastSpace;
            }
        }

        return text.substring(0, cutIndex).stripTrailing() + ell;
    }

    /**
     * Truncates text by maximum byte count using UTF-8 and default ellipsis ("...").
     */
    public static String truncateByBytes(String text, int maxBytes) {
        return truncateByBytes(text, maxBytes, StandardCharsets.UTF_8, DEFAULT_ELLIPSIS);
    }

    /**
     * Truncates text by maximum byte count with specified Charset and default ellipsis.
     */
    public static String truncateByBytes(String text, int maxBytes, Charset charset) {
        return truncateByBytes(text, maxBytes, charset, DEFAULT_ELLIPSIS);
    }

    /**
     * Truncates text by maximum byte count with specified Charset and custom ellipsis.
     * Guaranteed not to split multi-byte characters or surrogate pairs.
     *
     * @param text     input text, may be null
     * @param maxBytes maximum allowed bytes including ellipsis
     * @param charset  character set (e.g. UTF-8, EUC-KR)
     * @param ellipsis suffix appended when truncated
     * @return byte-safe truncated string, or null if input was null
     */
    public static String truncateByBytes(String text, int maxBytes, Charset charset, String ellipsis) {
        return truncateBytesInternal(text, maxBytes, charset, ellipsis, false);
    }

    /**
     * Truncates text by maximum byte count while preserving whole words.
     */
    public static String truncateByBytesPreservingWords(String text, int maxBytes, Charset charset, String ellipsis) {
        return truncateBytesInternal(text, maxBytes, charset, ellipsis, true);
    }

    private static String truncateBytesInternal(String text, int maxBytes, Charset charset, String ellipsis, boolean preserveWords) {
        if (text == null) {
            return null;
        }
        Objects.requireNonNull(charset, "charset must not be null");
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative: " + maxBytes);
        }

        byte[] textBytes = text.getBytes(charset);
        if (textBytes.length <= maxBytes) {
            return text;
        }

        String ell = (ellipsis != null) ? ellipsis : "";
        byte[] ellBytes = ell.getBytes(charset);
        if (ellBytes.length > maxBytes) {
            throw new IllegalArgumentException("maxBytes (" + maxBytes + ") must be >= ellipsis bytes (" + ellBytes.length + ")");
        }

        int budget = maxBytes - ellBytes.length;
        if (budget == 0) {
            return ell;
        }

        int currentBytes = 0;
        int safeEndIndex = 0;
        int len = text.length();
        int i = 0;

        while (i < len) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            String cpStr = text.substring(i, i + charCount);
            byte[] cpBytes = cpStr.getBytes(charset);

            if (currentBytes + cpBytes.length > budget) {
                break;
            }

            currentBytes += cpBytes.length;
            safeEndIndex = i + charCount;
            i += charCount;
        }

        if (preserveWords) {
            int lastSpace = -1;
            for (int k = safeEndIndex - 1; k >= 0; k--) {
                if (Character.isWhitespace(text.charAt(k))) {
                    lastSpace = k;
                    break;
                }
            }
            if (lastSpace > 0) {
                safeEndIndex = lastSpace;
            }
        }

        return text.substring(0, safeEndIndex).stripTrailing() + ell;
    }

    /**
     * Truncates text by word count using default ellipsis ("...").
     */
    public static String truncateWords(String text, int maxWords) {
        return truncateWords(text, maxWords, DEFAULT_ELLIPSIS);
    }

    /**
     * Truncates text by word count using custom ellipsis.
     *
     * @param text     input text, may be null
     * @param maxWords maximum number of words to preserve
     * @param ellipsis suffix appended when truncated
     * @return truncated text, or null if input was null
     */
    public static String truncateWords(String text, int maxWords, String ellipsis) {
        if (text == null) {
            return null;
        }
        if (maxWords < 0) {
            throw new IllegalArgumentException("maxWords must not be negative: " + maxWords);
        }
        if (maxWords == 0) {
            return (ellipsis != null) ? ellipsis : "";
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return text;
        }

        String[] tokens = trimmed.split("\\s+");
        if (tokens.length <= maxWords) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(tokens[i]);
        }

        if (ellipsis != null) {
            sb.append(ellipsis);
        }

        return sb.toString();
    }
}
