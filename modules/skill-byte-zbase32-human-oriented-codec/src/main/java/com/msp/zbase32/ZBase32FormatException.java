package com.msp.zbase32;

/**
 * Thrown when a z-base-32 encoded string contains invalid characters, illegal length,
 * or non-zero trailing padding bits in strict mode.
 */
public class ZBase32FormatException extends IllegalArgumentException {

    public ZBase32FormatException(String message) {
        super(message);
    }
}
