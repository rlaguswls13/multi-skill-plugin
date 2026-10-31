package com.msp.bulkhead;

/**
 * Exception thrown when a Bulkhead gate rejects execution due to concurrent capacity
 * and wait queue exhaustion or wait timeout expiration.
 */
public class BulkheadFullException extends RuntimeException {

    public BulkheadFullException(String message) {
        super(message);
    }

    public BulkheadFullException(String message, Throwable cause) {
        super(message, cause);
    }
}
