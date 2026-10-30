package com.msp.workerid;

/**
 * Worker ID 유효성 및 범위 검증기.
 */
public final class WorkerIdValidator {

    private WorkerIdValidator() {}

    public static boolean isValid(long workerId, long maxWorkerId) {
        return workerId >= 0 && workerId <= maxWorkerId;
    }

    public static void validate(long workerId, long maxWorkerId) {
        if (!isValid(workerId, maxWorkerId)) {
            throw new IllegalArgumentException(String.format("Worker ID %d out of valid range [0, %d]", workerId, maxWorkerId));
        }
    }
}
