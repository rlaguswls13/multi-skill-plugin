package com.msp.filewatch;

/**
 * Exception thrown when file watching fails.
 */
public class FileWatcherException extends RuntimeException {

    public FileWatcherException(String message) {
        super(message);
    }

    public FileWatcherException(String message, Throwable cause) {
        super(message, cause);
    }
}
