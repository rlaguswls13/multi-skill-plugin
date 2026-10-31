package com.msp.gzipstream;

/**
 * Unchecked exception thrown when an error occurs during compression or decompression.
 */
public class CompressionException extends RuntimeException {

    public CompressionException(String message) {
        super(message);
    }

    public CompressionException(String message, Throwable cause) {
        super(message, cause);
    }

    public CompressionException(Throwable cause) {
        super(cause);
    }
}
