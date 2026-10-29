package com.msp.cron;

/**
 * Exception thrown when cron parsing, evaluation, or scheduling fails.
 */
public class CronException extends RuntimeException {

    public CronException(String message) {
        super(message);
    }

    public CronException(String message, Throwable cause) {
        super(message, cause);
    }
}
