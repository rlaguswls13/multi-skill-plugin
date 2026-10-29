package com.msp.codec;

/**
 * Exception thrown when encoding or decoding operations fail.
 */
public class CodecException extends RuntimeException {

    public CodecException(String message) {
        super(message);
    }

    public CodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
