package com.msp.ini;

/**
 * Exception thrown when INI parsing or format operations fail.
 */
public class IniException extends RuntimeException {

    private final int lineNumber;

    public IniException(String message) {
        super(message);
        this.lineNumber = -1;
    }

    public IniException(String message, int lineNumber) {
        super(lineNumber >= 0 ? "Line " + lineNumber + ": " + message : message);
        this.lineNumber = lineNumber;
    }

    public IniException(String message, Throwable cause) {
        super(message, cause);
        this.lineNumber = -1;
    }

    public IniException(String message, int lineNumber, Throwable cause) {
        super(lineNumber >= 0 ? "Line " + lineNumber + ": " + message : message, cause);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
