package com.msp.isbn;

/**
 * Detailed information and validation result for an ISBN string.
 */
public record IsbnInfo(
        boolean valid,
        String raw,
        String clean,
        IsbnType type,
        String isbn10,
        String isbn13,
        String errorMessage
) {
    public static IsbnInfo success(String raw, String clean, IsbnType type, String isbn10, String isbn13) {
        return new IsbnInfo(true, raw, clean, type, isbn10, isbn13, null);
    }

    public static IsbnInfo failure(String raw, String errorMessage) {
        return new IsbnInfo(false, raw, null, IsbnType.UNKNOWN, null, null, errorMessage);
    }
}
