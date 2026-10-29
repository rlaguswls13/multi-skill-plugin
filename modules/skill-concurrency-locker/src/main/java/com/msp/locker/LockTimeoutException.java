package com.msp.locker;

import java.time.Duration;

/**
 * Exception thrown when a thread fails to acquire a keyed lock within the specified timeout.
 */
public class LockTimeoutException extends RuntimeException {

    private final Object key;
    private final Duration timeout;

    public LockTimeoutException(Object key, Duration timeout) {
        super("Failed to acquire lock for key '" + key + "' within timeout: " + timeout);
        this.key = key;
        this.timeout = timeout;
    }

    public Object getKey() {
        return key;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
