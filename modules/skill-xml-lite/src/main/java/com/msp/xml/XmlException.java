package com.msp.xml;

/**
 * Exception thrown when XML parsing or formatting fails.
 */
public class XmlException extends RuntimeException {

    public XmlException(String message) {
        super(message);
    }

    public XmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
