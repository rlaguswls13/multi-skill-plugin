package com.msp.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ResultAndTryTest {

    @Test
    @DisplayName("Result.success() 성공 케이스 기본 및 체이닝 동작 검증")
    void testResultSuccessChaining() {
        Result<Integer, String> result = Result.success(10);

        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals(10, result.getOrNull());
        assertNull(result.getErrorOrNull());
        assertEquals(10, result.getOrElse(0));
        assertEquals(10, result.getOrThrow());

        // map
        Result<String, String> mapped = result.map(n -> "Count: " + (n * 2));
        assertEquals("Count: 20", mapped.getOrNull());

        // flatMap
        Result<Double, String> flatMapped = result.flatMap(n -> Result.success(n / 2.0));
        assertEquals(5.0, flatMapped.getOrNull());

        // filter
        Result<Integer, String> filteredPass = result.filter(n -> n > 5, n -> "Too small");
        assertTrue(filteredPass.isSuccess());

        Result<Integer, String> filteredFail = result.filter(n -> n > 20, n -> "Too small: " + n);
        assertTrue(filteredFail.isFailure());
        assertEquals("Too small: 10", filteredFail.getErrorOrNull());

        // fold
        String folded = result.fold(val -> "OK: " + val, err -> "ERR: " + err);
        assertEquals("OK: 10", folded);
    }

    @Test
    @DisplayName("Result.failure() 실패 케이스 기본 및 에러 복구(recover) 동작 검증")
    void testResultFailureAndRecover() {
        Result<Integer, String> failure = Result.failure("INVALID_INPUT");

        assertFalse(failure.isSuccess());
        assertTrue(failure.isFailure());
        assertNull(failure.getOrNull());
        assertEquals("INVALID_INPUT", failure.getErrorOrNull());
        assertEquals(99, failure.getOrElse(99));

        assertThrows(NoSuchElementException.class, failure::getOrThrow);
        assertThrows(IllegalArgumentException.class, () -> failure.getOrThrow(IllegalArgumentException::new));

        // recover
        Result<Integer, String> recovered = failure.recover(err -> -1);
        assertTrue(recovered.isSuccess());
        assertEquals(-1, recovered.getOrNull());

        // mapError
        Result<Integer, Integer> mappedError = failure.mapError(String::length);
        assertEquals(13, mappedError.getErrorOrNull());
    }

    @Test
    @DisplayName("Try.of() 예외 안전 캡처 및 Result 변환 검증")
    void testTryExecutionAndConversion() {
        // 성공 케이스
        Try<String> successTry = Try.of(() -> "data_loaded");
        assertTrue(successTry.isSuccess());
        assertEquals("data_loaded", successTry.getOrNull());

        // 예외 발생 케이스
        Try<String> failureTry = Try.of(() -> {
            throw new IOException("Disk error");
        });
        assertTrue(failureTry.isFailure());
        assertInstanceOf(IOException.class, failureTry.getCause());
        assertEquals("default_fallback", failureTry.getOrElse("default_fallback"));

        // recover
        Try<String> recovered = failureTry.recover(err -> "recovered_" + err.getMessage());
        assertEquals("recovered_Disk error", recovered.getOrNull());

        // toResult(mapper)
        Result<String, String> res = failureTry.toResult(err -> "ERR_CODE: " + err.getMessage());
        assertTrue(res.isFailure());
        assertEquals("ERR_CODE: Disk error", res.getErrorOrNull());
    }

    @Test
    @DisplayName("Try.run() void 작업의 예외 안전 실행 검증")
    void testTryRun() {
        AtomicBoolean flag = new AtomicBoolean(false);
        Try<Void> voidSuccess = Try.run(() -> flag.set(true));
        assertTrue(voidSuccess.isSuccess());
        assertTrue(flag.get());

        Try<Void> voidFailure = Try.run(() -> {
            throw new IllegalStateException("State error");
        });
        assertTrue(voidFailure.isFailure());
        assertInstanceOf(IllegalStateException.class, voidFailure.getCause());
    }
}
