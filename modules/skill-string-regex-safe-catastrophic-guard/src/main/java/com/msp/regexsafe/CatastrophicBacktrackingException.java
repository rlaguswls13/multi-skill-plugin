package com.msp.regexsafe;

public class CatastrophicBacktrackingException extends RuntimeException {
    private final long steps;

    public CatastrophicBacktrackingException(String message, long steps) {
        super(message);
        this.steps = steps;
    }

    public long getSteps() {
        return steps;
    }
}
