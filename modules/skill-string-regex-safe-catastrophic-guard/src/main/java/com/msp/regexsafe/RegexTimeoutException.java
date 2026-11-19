package com.msp.regexsafe;

import java.time.Duration;

public class RegexTimeoutException extends RuntimeException {
    private final Duration elapsed;

    public RegexTimeoutException(String message, Duration elapsed) {
        super(message);
        this.elapsed = elapsed;
    }

    public Duration getElapsed() {
        return elapsed;
    }
}
