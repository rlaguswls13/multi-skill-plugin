package com.msp.blackout;

/**
 * Runtime exception thrown when a scheduled execution conflicts with an active blackout window
 * under {@link BlackoutPolicy#REJECT}, or when blackout windows leave no allowed execution time.
 */
public final class BlackoutConflictException extends RuntimeException {
    public BlackoutConflictException(String message) {
        super(message);
    }
}
