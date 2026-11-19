package com.msp.regexsafe;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CharSequence decorator that tracks character evaluations and enforces step/time limits
 * to guard against catastrophic backtracking (ReDoS).
 */
public class StepCountingCharSequence implements CharSequence {

    private final CharSequence inner;
    private final AtomicLong stepCounter;
    private final long maxSteps;
    private final long deadlineNanos;
    private final long startNanos;

    public StepCountingCharSequence(CharSequence inner, long maxSteps, Duration timeout) {
        this(
                Objects.requireNonNull(inner, "inner CharSequence cannot be null"),
                new AtomicLong(0),
                maxSteps,
                System.nanoTime(),
                System.nanoTime() + timeout.toNanos()
        );
    }

    private StepCountingCharSequence(CharSequence inner, AtomicLong stepCounter, long maxSteps, long startNanos, long deadlineNanos) {
        this.inner = inner;
        this.stepCounter = stepCounter;
        this.maxSteps = maxSteps;
        this.startNanos = startNanos;
        this.deadlineNanos = deadlineNanos;
    }

    private void countStep() {
        long current = stepCounter.incrementAndGet();
        if (current > maxSteps) {
            throw new CatastrophicBacktrackingException(
                    "Backtracking step limit exceeded: " + current + " > " + maxSteps, current);
        }
        if ((current & 0x1FF) == 0) { // Check time every 512 steps
            long now = System.nanoTime();
            if (now > deadlineNanos) {
                Duration elapsed = Duration.ofNanos(now - startNanos);
                throw new RegexTimeoutException("Regex evaluation timed out after " + elapsed.toMillis() + "ms", elapsed);
            }
        }
    }

    public long getStepCount() {
        return stepCounter.get();
    }

    @Override
    public int length() {
        return inner.length();
    }

    @Override
    public char charAt(int index) {
        countStep();
        return inner.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        countStep();
        CharSequence sub = inner.subSequence(start, end);
        return new StepCountingCharSequence(sub, stepCounter, maxSteps, startNanos, deadlineNanos);
    }

    @Override
    public String toString() {
        return inner.toString();
    }
}
