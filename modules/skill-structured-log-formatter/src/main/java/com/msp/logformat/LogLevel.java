package com.msp.logformat;

/**
 * Standard log severity levels.
 */
public enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR;

    public static LogLevel of(String name) {
        if (name == null || name.isBlank()) {
            return INFO;
        }
        try {
            return LogLevel.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}
