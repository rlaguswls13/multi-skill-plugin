package com.msp.props;

/**
 * Exception thrown when property loading, parsing, or conversion fails.
 */
public class PropertiesLoaderException extends RuntimeException {

    public PropertiesLoaderException(String message) {
        super(message);
    }

    public PropertiesLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
