package com.msp.yaml;

/**
 * Exception thrown when YAML parsing or serialization fails.
 */
public class YamlException extends RuntimeException {

    private final int line;

    public YamlException(String message) {
        super(message);
        this.line = -1;
    }

    public YamlException(String message, int line) {
        super(line >= 0 ? "Line " + line + ": " + message : message);
        this.line = line;
    }

    public YamlException(String message, Throwable cause) {
        super(message, cause);
        this.line = -1;
    }

    public YamlException(String message, int line, Throwable cause) {
        super(line >= 0 ? "Line " + line + ": " + message : message, cause);
        this.line = line;
    }

    public int getLine() {
        return line;
    }
}
