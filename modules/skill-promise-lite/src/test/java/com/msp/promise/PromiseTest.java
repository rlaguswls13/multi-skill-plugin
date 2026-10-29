package com.msp.promise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PromiseTest {

    @Test
    @DisplayName("resolve, supplyAsync and chaining with thenApply and thenCompose")
    void chainingSuccess() {
        Promise<Integer> initial = Promise.resolve(10);

        Promise<String> transformed = initial
                .thenApply(n -> n * 2)
                .thenCompose(n -> Promise.supplyAsync(() -> "Result: " + n));

        assertEquals("Result: 20", transformed.join());
    }

    @Test
    @DisplayName("Promise.create with JavaScript style (resolve, reject)")
    void promiseCreateFactory() {
        Promise<String> p1 = Promise.create((resolve, reject) -> resolve.accept("hello"));
        assertEquals("hello", p1.join());

        Promise<String> p2 = Promise.create((resolve, reject) -> reject.accept(new IllegalStateException("boom")));
        assertTrue(p2.isCompletedExceptionally());
        assertThrows(CompletionException.class, p2::join);
    }

    @Test
    @DisplayName("catchError should recover with fallback value")
    void catchErrorGeneral() {
        Promise<String> failed = Promise.<String>supplyAsync(() -> {
            throw new IllegalArgumentException("invalid input");
        }).catchError(err -> "recovered:" + err.getMessage());

        assertEquals("recovered:invalid input", failed.join());
    }

    @Test
    @DisplayName("catchError with specific class should only catch matching exception type")
    void catchErrorSpecificType() {
        // Matched type recovers
        Promise<String> matched = Promise.<String>supplyAsync(() -> {
            throw new IllegalArgumentException("bad argument");
        }).catchError(IllegalArgumentException.class, err -> "caught-illegal-argument");

        assertEquals("caught-illegal-argument", matched.join());

        // Unmatched type propagates
        Promise<String> unmatched = Promise.<String>supplyAsync(() -> {
            throw new IllegalStateException("bad state");
        }).catchError(IllegalArgumentException.class, err -> "should-not-catch");

        assertThrows(CompletionException.class, unmatched::join);
    }

    @Test
    @DisplayName("finallyDo executes regardless of success or failure")
    void finallyDoExecution() {
        AtomicBoolean successFinallyRan = new AtomicBoolean(false);
        Promise.resolve("ok")
                .finallyDo(() -> successFinallyRan.set(true))
                .join();
        assertTrue(successFinallyRan.get());

        AtomicBoolean failFinallyRan = new AtomicBoolean(false);
        try {
            Promise.reject(new RuntimeException("err"))
                    .finallyDo(() -> failFinallyRan.set(true))
                    .join();
        } catch (Throwable ignored) {}
        assertTrue(failFinallyRan.get());
    }

    @Test
    @DisplayName("timeout with fallback value and fallback supplier")
    void timeoutWithFallback() {
        Promise<String> slowPromise = Promise.supplyAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
            return "too-late";
        });

        Promise<String> timedOut = slowPromise.timeout(Duration.ofMillis(30), "fallback-value");
        assertEquals("fallback-value", timedOut.join());

        Promise<String> slowSupplierPromise = Promise.supplyAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
            return "too-late";
        });

        Promise<String> supplierTimedOut = slowSupplierPromise.timeout(Duration.ofMillis(30), () -> "supplier-fallback");
        assertEquals("supplier-fallback", supplierTimedOut.join());
    }

    @Test
    @DisplayName("timeout without fallback should throw TimeoutException")
    void timeoutExceptionThrown() {
        Promise<String> slow = Promise.supplyAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
            return "done";
        }).timeout(Duration.ofMillis(20));

        CompletionException ce = assertThrows(CompletionException.class, slow::join);
        assertTrue(ce.getCause() instanceof TimeoutException);
    }

    @Test
    @DisplayName("allOf: waits for all promises to resolve in order")
    void allOfResolution() {
        Promise<Integer> p1 = Promise.supplyAsync(() -> 1);
        Promise<String> p2 = Promise.supplyAsync(() -> "two");
        Promise<Boolean> p3 = Promise.resolve(true);

        Promise<List<Object>> all = Promise.allOf(p1, p2, p3);
        List<Object> results = all.join();

        assertEquals(List.of(1, "two", true), results);
    }

    @Test
    @DisplayName("allOf fails fast if any promise rejects")
    void allOfFailure() {
        Promise<Integer> p1 = Promise.resolve(1);
        Promise<Integer> p2 = Promise.reject(new RuntimeException("failed"));
        Promise<Integer> p3 = Promise.resolve(3);

        Promise<List<Object>> all = Promise.allOf(p1, p2, p3);
        assertThrows(CompletionException.class, all::join);
    }

    @Test
    @DisplayName("anyOf: fulfills with first successful promise, ignoring earlier failure")
    void anyOfFirstSuccess() {
        Promise<String> failing = Promise.supplyAsync(() -> {
            throw new RuntimeException("fail fast");
        });

        Promise<String> succeeding = Promise.supplyAsync(() -> {
            try {
                Thread.sleep(40);
            } catch (InterruptedException ignored) {}
            return "winner";
        });

        Promise<Object> any = Promise.anyOf(failing, succeeding);
        assertEquals("winner", any.join());
    }

    @Test
    @DisplayName("anyOf: rejects with AggregateException if all promises fail")
    void anyOfAllReject() {
        Promise<String> f1 = Promise.reject(new IllegalArgumentException("error 1"));
        Promise<String> f2 = Promise.reject(new IllegalStateException("error 2"));

        Promise<Object> any = Promise.anyOf(f1, f2);
        CompletionException ce = assertThrows(CompletionException.class, any::join);
        assertTrue(ce.getCause() instanceof AggregateException);
        AggregateException ae = (AggregateException) ce.getCause();
        assertEquals(2, ae.getCauses().size());
    }

    @Test
    @DisplayName("race: settles immediately with whichever completes first")
    void raceCondition() {
        Promise<String> fast = Promise.supplyAsync(() -> "first");
        Promise<String> slow = Promise.supplyAsync(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {}
            return "second";
        });

        Promise<Object> winner = Promise.race(fast, slow);
        assertEquals("first", winner.join());
    }

    @Test
    @DisplayName("orElse and orElseGet utility methods")
    void orElseHelpers() {
        Promise<String> ok = Promise.resolve("hello");
        assertEquals("hello", ok.orElse("default"));
        assertEquals("hello", ok.orElseGet(() -> "default"));

        Promise<String> failed = Promise.reject(new RuntimeException("crash"));
        assertEquals("fallback", failed.orElse("fallback"));
        assertEquals("fallback-supplier", failed.orElseGet(() -> "fallback-supplier"));
    }
}
