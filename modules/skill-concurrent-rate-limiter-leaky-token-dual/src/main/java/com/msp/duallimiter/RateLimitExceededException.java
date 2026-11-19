package com.msp.duallimiter;

/**
 * Exception thrown when a rate limit request is rejected by the dual rate limiter.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String stage;

    public RateLimitExceededException(String stage, String message) {
        super(message);
        this.stage = stage;
    }

    /**
     * Identifies the stage that rejected the request ("TOKEN_BUCKET" or "LEAKY_QUEUE").
     */
    public String getStage() {
        return stage;
    }
}
