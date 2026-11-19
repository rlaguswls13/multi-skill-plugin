package com.msp.problemdetails;

import java.util.Objects;

/**
 * RuntimeException wrapping a ProblemDetails instance for straightforward integration
 * with controller advice, exception handlers, and error pipelines.
 */
public class ProblemDetailsException extends RuntimeException {

    private final ProblemDetails problemDetails;

    public ProblemDetailsException(ProblemDetails problemDetails) {
        super(problemDetails != null ? problemDetails.getTitle() + (problemDetails.getDetail() != null ? ": " + problemDetails.getDetail() : "") : "Problem Details Exception");
        this.problemDetails = Objects.requireNonNull(problemDetails, "problemDetails must not be null");
    }

    public ProblemDetailsException(ProblemDetails problemDetails, Throwable cause) {
        super(problemDetails != null ? problemDetails.getTitle() + (problemDetails.getDetail() != null ? ": " + problemDetails.getDetail() : "") : "Problem Details Exception", cause);
        this.problemDetails = Objects.requireNonNull(problemDetails, "problemDetails must not be null");
    }

    public ProblemDetails getProblemDetails() {
        return problemDetails;
    }
}
