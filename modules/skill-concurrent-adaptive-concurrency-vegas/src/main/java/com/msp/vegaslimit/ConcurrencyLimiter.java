package com.msp.vegaslimit;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Thread-safe adaptive concurrency limiter backed by a {@link LimitAlgorithm} (TCP Vegas or Gradient RTT).
 * <p>
 * Callers acquire a concurrency slot via {@link #tryAcquire()}, then invoke exactly one terminal callback on
 * the returned {@link Listener}: {@link Listener#onSuccess()}, {@link Listener#onDropped()}, or
 * {@link Listener#onIgnore()}.
 */
public final class ConcurrencyLimiter {

    /**
     * Per-request completion handle. Terminal methods are idempotent — subsequent calls are no-ops.
     */
    public interface Listener {
        /** Marks the request as completed successfully and updates the concurrency limit using measured RTT. */
        void onSuccess();

        /** Marks the request as dropped/timed-out/throttled downstream, triggering limit backoff. */
        void onDropped();

        /** Releases the concurrency slot without feeding the sample into the limit algorithm. */
        void onIgnore();
    }

    /**
     * Immutable point-in-time metrics snapshot.
     */
    public record MetricsSnapshot(
            int currentLimit,
            int inFlight,
            long minRttNanos,
            long lastRttNanos,
            double estimatedQueueSize,
            long totalAcquired,
            long totalRejected,
            long totalDropped
    ) {
    }

    private final LimitAlgorithm algorithm;
    private final LongSupplier nanoClock;
    private final Object algorithmLock = new Object();

    private final AtomicInteger inFlight = new AtomicInteger(0);
    private volatile int cachedLimit;
    private final AtomicLong totalAcquired = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);

    public ConcurrencyLimiter(LimitAlgorithm algorithm) {
        this(algorithm, System::nanoTime);
    }

    public ConcurrencyLimiter(LimitAlgorithm algorithm, LongSupplier nanoClock) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock must not be null");
        this.cachedLimit = algorithm.currentLimit();
    }

    /**
     * Attempts to acquire an execution permit without blocking.
     *
     * @return {@link Optional} containing a {@link Listener} if acquired, or empty if concurrency limit is reached.
     */
    public Optional<Listener> tryAcquire() {
        while (true) {
            int current = inFlight.get();
            int limit = cachedLimit;
            if (current >= limit) {
                totalRejected.incrementAndGet();
                return Optional.empty();
            }
            if (inFlight.compareAndSet(current, current + 1)) {
                totalAcquired.incrementAndGet();
                long startNanos = nanoClock.getAsLong();
                return Optional.of(new PermitListener(startNanos));
            }
        }
    }

    public int currentLimit() {
        return cachedLimit;
    }

    public int inFlight() {
        return inFlight.get();
    }

    public MetricsSnapshot snapshot() {
        synchronized (algorithmLock) {
            return new MetricsSnapshot(
                    algorithm.currentLimit(),
                    inFlight.get(),
                    algorithm.minRttNanos(),
                    algorithm.lastRttNanos(),
                    algorithm.estimatedQueueSize(),
                    totalAcquired.get(),
                    totalRejected.get(),
                    totalDropped.get()
            );
        }
    }

    private final class PermitListener implements Listener {
        private final long startNanos;
        private final AtomicBoolean completed = new AtomicBoolean(false);

        private PermitListener(long startNanos) {
            this.startNanos = startNanos;
        }

        @Override
        public void onSuccess() {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            int observedInFlight = inFlight.get();
            inFlight.decrementAndGet();
            long rtt = Math.max(0L, nanoClock.getAsLong() - startNanos);
            synchronized (algorithmLock) {
                cachedLimit = algorithm.onSample(rtt, observedInFlight, false);
            }
        }

        @Override
        public void onDropped() {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            int observedInFlight = inFlight.get();
            inFlight.decrementAndGet();
            totalDropped.incrementAndGet();
            long rtt = Math.max(0L, nanoClock.getAsLong() - startNanos);
            synchronized (algorithmLock) {
                cachedLimit = algorithm.onSample(rtt, observedInFlight, true);
            }
        }

        @Override
        public void onIgnore() {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            inFlight.decrementAndGet();
        }
    }
}
