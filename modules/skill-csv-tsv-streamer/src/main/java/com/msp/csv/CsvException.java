package com.msp.csv;

/**
 * Exception thrown when CSV/TSV parsing or writing fails.
 */
public class CsvException extends RuntimeException {

    public CsvException(String message) {
        super(message);
    }

    public CsvException(String message, Throwable cause) {
        super(message, cause);
    }
}
