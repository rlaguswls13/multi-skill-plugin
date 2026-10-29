package com.msp.isbn;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalization, sanitization, and formatting utility for ISBN strings.
 */
public final class IsbnFormatter {

    private static final Pattern STRIP_PATTERN = Pattern.compile("[\\s-]+");

    private IsbnFormatter() {
    }

    /**
     * Cleans an ISBN string by stripping whitespace and hyphens, and converting 'x' to uppercase 'X'.
     */
    public static String clean(String isbn) {
        if (isbn == null) {
            return "";
        }
        String stripped = STRIP_PATTERN.matcher(isbn.trim()).replaceAll("");
        return stripped.toUpperCase();
    }

    /**
     * Formats an ISBN-13 into standard hyphenated representation (e.g. 978-0-306-40615-7).
     * Format pattern: prefix(3)-group(1)-publisher(3)-title(5)-check(1)
     */
    public static String formatIsbn13(String isbn13) {
        Objects.requireNonNull(isbn13, "isbn13 must not be null");
        String c = clean(isbn13);
        if (c.length() != 13) {
            return isbn13;
        }
        return c.substring(0, 3) + "-" + c.charAt(3) + "-" + c.substring(4, 7) + "-" + c.substring(7, 12) + "-" + c.charAt(12);
    }

    /**
     * Formats an ISBN-10 into standard hyphenated representation (e.g. 0-306-40615-2).
     * Format pattern: group(1)-publisher(3)-title(5)-check(1)
     */
    public static String formatIsbn10(String isbn10) {
        Objects.requireNonNull(isbn10, "isbn10 must not be null");
        String c = clean(isbn10);
        if (c.length() != 10) {
            return isbn10;
        }
        return c.charAt(0) + "-" + c.substring(1, 4) + "-" + c.substring(4, 9) + "-" + c.charAt(9);
    }
}
