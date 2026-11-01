package com.msp.etag;

import java.util.Objects;

/**
 * Result of evaluating RFC 7232 HTTP conditional request headers.
 */
public final class ConditionalEvaluationResult {

    private final boolean passed;
    private final int statusCode;
    private final String failedHeader;
    private final String reason;

    public ConditionalEvaluationResult(boolean passed, int statusCode, String failedHeader, String reason) {
        this.passed = passed;
        this.statusCode = statusCode;
        this.failedHeader = failedHeader;
        this.reason = reason;
    }

    public boolean isPassed() {
        return passed;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getFailedHeader() {
        return failedHeader;
    }

    public String getReason() {
        return reason;
    }

    public static ConditionalEvaluationResult passed() {
        return new ConditionalEvaluationResult(true, 200, null, "Preconditions evaluated successfully; proceed with request");
    }

    public static ConditionalEvaluationResult notModified(String failedHeader, String reason) {
        return new ConditionalEvaluationResult(false, 304, failedHeader, reason);
    }

    public static ConditionalEvaluationResult preconditionFailed(String failedHeader, String reason) {
        return new ConditionalEvaluationResult(false, 412, failedHeader, reason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionalEvaluationResult that = (ConditionalEvaluationResult) o;
        return passed == that.passed &&
                statusCode == that.statusCode &&
                Objects.equals(failedHeader, that.failedHeader) &&
                Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passed, statusCode, failedHeader, reason);
    }

    @Override
    public String toString() {
        return "ConditionalEvaluationResult{" +
                "passed=" + passed +
                ", statusCode=" + statusCode +
                ", failedHeader='" + failedHeader + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
