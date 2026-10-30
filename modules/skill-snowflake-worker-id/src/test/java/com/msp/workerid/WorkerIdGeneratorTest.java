package com.msp.workerid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkerIdGeneratorTest {

    @Test
    @DisplayName("Auto assign worker ID within valid range [0, 1023]")
    void testAutoAssign() {
        int maxWorkerId = 1023;
        int workerId = WorkerIdGenerator.autoAssign(maxWorkerId);
        assertTrue(workerId >= 0 && workerId <= maxWorkerId);
        assertTrue(WorkerIdValidator.isValid(workerId, maxWorkerId));
        assertDoesNotThrow(() -> WorkerIdValidator.validate(workerId, maxWorkerId));
    }

    @Test
    @DisplayName("From PID and IP within range")
    void testFromPidAndIp() {
        int max = 31;
        int id = WorkerIdGenerator.fromPidAndIp(max);
        assertTrue(id >= 0 && id <= max);
    }

    @Test
    @DisplayName("Validator throws for out of bounds worker id")
    void testValidatorException() {
        assertFalse(WorkerIdValidator.isValid(-1, 1023));
        assertFalse(WorkerIdValidator.isValid(1024, 1023));
        assertThrows(IllegalArgumentException.class, () -> WorkerIdValidator.validate(1024, 1023));
    }
}
