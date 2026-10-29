package com.msp.bytepack;

/**
 * Exception thrown for binary buffer operations like underflow, overflow, or malformed data.
 */
public class ByteBufferException extends RuntimeException {
    public ByteBufferException(String message) {
        super(message);
    }

    public ByteBufferException(String message, Throwable cause) {
        super(message, cause);
    }
}
