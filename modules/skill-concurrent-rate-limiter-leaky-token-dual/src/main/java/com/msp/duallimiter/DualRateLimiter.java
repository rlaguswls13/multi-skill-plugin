package com.msp.duallimiter;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Dual-Stage Hybrid Rate Limiter combining Token Bucket (burst tolerance)
 * and Leaky Bucket (smooth uniform emission to downstream systems).
 * <p>
 * <ul>
 *   <li><b>Stage 1 (Token Bucket)</b>: Absorbs burst traffic up to {@code burstCapacity}
 *       and refills tokens at {@code tokenRefillRate} per second.</li>
 *   <li><b>Stage 2 (Leaky Bucket)</b>: Buffers admitted requests up to {@code queueCapacity}
 *       and drains them at a smooth, constant {@code leakRate} per second, eliminating micro-bursts.</li>
 * </ul>
 */
public final class DualRateLimiter implements AutoCloseable {

    private final double tokenCapacity;
    private final double tokenRefillRate;
    private final double leakRate;
    private final int queueCapacity;
    private final long leakIntervalNanos;

    // Token Bucket state
    private final ReentrantLock tokenLock = new ReentrantLock();
    private double availableTokens;
    private long lastTokenRefillNanos;

    // Leaky Bucket synchronous water state
    private final ReentrantLock leakLock = new ReentrantLock();
    private double currentWater;
    private long lastLeakNanos;

    // Leaky Queue for scheduled tasks
    private final BlockingQueue<ScheduledTask<?>> taskQueue;
    private final Thread drainerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Metrics
    private final AtomicLong acceptedCounter = new AtomicLong();
    private final AtomicLong rejectedCounter = new AtomicLong();
    private final AtomicLong drainedCounter = new AtomicLong();

    private record ScheduledTask<T>(Supplier<T> task, CompletableFuture<T> future) {
        void run() {
            try {
                T result = task.get();
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
    }

    private DualRateLimiter(Builder builder) {
        this.tokenCapacity = builder.tokenCapacity;
        this.tokenRefillRate = builder.tokenRefillRate;
        this.leakRate = builder.leakRate;
        this.queueCapacity = builder.queueCapacity;
        this.leakIntervalNanos = (long) (1_000_000_000.0 / leakRate);

        this.availableTokens = builder.initialTokens >= 0 ? builder.initialTokens : builder.tokenCapacity;
        this.lastTokenRefillNanos = System.nanoTime();

        this.currentWater = 0.0;
        this.lastLeakNanos = System.nanoTime();

        this.taskQueue = new LinkedBlockingQueue<>(builder.queueCapacity);

        this.drainerThread = Thread.ofPlatform()
                .name("dual-limiter-drainer-" + System.identityHashCode(this))
                .daemon(true)
                .start(this::drainLoop);
    }

    /**
     * Creates a new Builder for configuring a DualRateLimiter.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Attempts to synchronously acquire 1 permit without blocking.
     * Evaluates both Token Bucket (burst) and Leaky Bucket (smoothness) capacity.
     *
     * @return {@code true} if permit is acquired, {@code false} otherwise
     */
    public boolean tryAcquire() {
        return tryAcquire(1.0);
    }

    /**
     * Attempts to synchronously acquire the requested permits without blocking.
     *
     * @param permits number of permits
     * @return {@code true} if acquired, {@code false} otherwise
     */
    public boolean tryAcquire(double permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
        if (!running.get()) {
            return false;
        }

        // 1. Stage 1: Try acquiring token
        tokenLock.lock();
        try {
            refillTokens();
            if (availableTokens < permits) {
                rejectedCounter.incrementAndGet();
                return false;
            }

            // 2. Stage 2: Check leaky bucket instantaneous water level
            leakLock.lock();
            try {
                leakWater();
                if (currentWater + permits > queueCapacity) {
                    rejectedCounter.incrementAndGet();
                    return false;
                }
                // Both stages pass
                availableTokens -= permits;
                currentWater += permits;
                acceptedCounter.incrementAndGet();
                return true;
            } finally {
                leakLock.unlock();
            }
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Schedules an asynchronous task through the dual-stage pipeline.
     * <ol>
     *   <li>Verifies and consumes burst token from Token Bucket.</li>
     *   <li>Enqueues task into the Leaky Bucket queue.</li>
     *   <li>Drains task at uniform rate {@code leakRate} to downstream.</li>
     * </ol>
     *
     * @param task supplier producing the result
     * @param <T>  result type
     * @return CompletableFuture completing when task is drained and executed
     */
    public <T> CompletableFuture<T> executeScheduled(Supplier<T> task) {
        Objects.requireNonNull(task, "task must not be null");

        if (!running.get()) {
            rejectedCounter.incrementAndGet();
            return CompletableFuture.failedFuture(
                    new IllegalStateException("DualRateLimiter is closed"));
        }

        // 1. Stage 1: Token Bucket check
        tokenLock.lock();
        boolean tokenAcquired = false;
        try {
            refillTokens();
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                tokenAcquired = true;
            }
        } finally {
            tokenLock.unlock();
        }

        if (!tokenAcquired) {
            rejectedCounter.incrementAndGet();
            return CompletableFuture.failedFuture(
                    new RateLimitExceededException("TOKEN_BUCKET", "Token bucket burst capacity exceeded"));
        }

        // 2. Stage 2: Enqueue into Leaky Bucket
        CompletableFuture<T> future = new CompletableFuture<>();
        ScheduledTask<T> scheduledTask = new ScheduledTask<>(task, future);

        if (!taskQueue.offer(scheduledTask)) {
            // Queue full: refund token
            tokenLock.lock();
            try {
                availableTokens = Math.min(tokenCapacity, availableTokens + 1.0);
            } finally {
                tokenLock.unlock();
            }
            rejectedCounter.incrementAndGet();
            return CompletableFuture.failedFuture(
                    new RateLimitExceededException("LEAKY_QUEUE", "Leaky bucket queue capacity (" + queueCapacity + ") full"));
        }

        acceptedCounter.incrementAndGet();
        return future;
    }

    /**
     * Schedules a Runnable task through the dual-stage rate limiter.
     *
     * @param task runnable to execute
     * @return CompletableFuture completing when runnable finishes
     */
    public CompletableFuture<Void> runScheduled(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        return executeScheduled(() -> {
            task.run();
            return null;
        });
    }

    private void drainLoop() {
        long nextDrainNanos = System.nanoTime() + leakIntervalNanos;

        while (running.get() || !taskQueue.isEmpty()) {
            if (taskQueue.peek() == null) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            // Enforce uniform leak interval while keeping task buffered in queue
            long now = System.nanoTime();
            if (nextDrainNanos > now) {
                long sleepNanos = nextDrainNanos - now;
                try {
                    TimeUnit.NANOSECONDS.sleep(sleepNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            ScheduledTask<?> task = taskQueue.poll();
            if (task == null) {
                continue;
            }

            nextDrainNanos = Math.max(System.nanoTime(), nextDrainNanos) + leakIntervalNanos;

            try {
                task.run();
                drainedCounter.incrementAndGet();
            } catch (Throwable ignored) {
            }
        }

        // Fail any remaining tasks on shutdown
        ScheduledTask<?> abandoned;
        while ((abandoned = taskQueue.poll()) != null) {
            abandoned.future().completeExceptionally(new IllegalStateException("DualRateLimiter closed"));
        }
    }

    private void refillTokens() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastTokenRefillNanos;
        if (elapsedNanos > 0) {
            double refill = (elapsedNanos / 1_000_000_000.0) * tokenRefillRate;
            availableTokens = Math.min(tokenCapacity, availableTokens + refill);
            lastTokenRefillNanos = now;
        }
    }

    private void leakWater() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastLeakNanos;
        if (elapsedNanos > 0) {
            double leaked = (elapsedNanos / 1_000_000_000.0) * leakRate;
            currentWater = Math.max(0.0, currentWater - leaked);
            lastLeakNanos = now;
        }
    }

    /**
     * Current number of tokens in the Token Bucket.
     */
    public double availableTokens() {
        tokenLock.lock();
        try {
            refillTokens();
            return availableTokens;
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Current number of scheduled tasks in the Leaky Bucket queue.
     */
    public int leakQueueSize() {
        return taskQueue.size();
    }

    public long totalAccepted() {
        return acceptedCounter.get();
    }

    public long totalRejected() {
        return rejectedCounter.get();
    }

    public long totalDrained() {
        return drainedCounter.get();
    }

    /**
     * Estimated delay until a newly scheduled task will be drained.
     */
    public Duration estimatedDelay() {
        int q = taskQueue.size();
        if (q == 0 || leakRate <= 0.0) {
            return Duration.ZERO;
        }
        long nanos = (long) ((q / leakRate) * 1_000_000_000.0);
        return Duration.ofNanos(nanos);
    }

    /**
     * Captures an atomic-like snapshot of limiter state.
     */
    public DualLimiterSnapshot snapshot() {
        tokenLock.lock();
        try {
            refillTokens();
            return new DualLimiterSnapshot(
                    availableTokens,
                    tokenCapacity,
                    tokenRefillRate,
                    taskQueue.size(),
                    queueCapacity,
                    leakRate,
                    acceptedCounter.get(),
                    rejectedCounter.get(),
                    drainedCounter.get()
            );
        } finally {
            tokenLock.unlock();
        }
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            drainerThread.interrupt();
            try {
                drainerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Builder for {@link DualRateLimiter}.
     */
    public static final class Builder {
        private double tokenCapacity = 50.0;
        private double tokenRefillRate = 10.0;
        private double leakRate = 10.0;
        private int queueCapacity = 100;
        private double initialTokens = -1.0;

        private Builder() {}

        /**
         * Sets the maximum burst token capacity of Stage 1.
         */
        public Builder tokenCapacity(double capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("tokenCapacity must be > 0");
            this.tokenCapacity = capacity;
            return this;
        }

        /**
         * Sets the token refill rate in tokens/second.
         */
        public Builder tokenRefillRate(double rate) {
            if (rate <= 0) throw new IllegalArgumentException("tokenRefillRate must be > 0");
            this.tokenRefillRate = rate;
            return this;
        }

        /**
         * Sets the Stage 2 uniform drain/leak rate in items/second.
         */
        public Builder leakRate(double rate) {
            if (rate <= 0) throw new IllegalArgumentException("leakRate must be > 0");
            this.leakRate = rate;
            return this;
        }

        /**
         * Sets the Stage 2 maximum leaky bucket queue capacity.
         */
        public Builder queueCapacity(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("queueCapacity must be > 0");
            this.queueCapacity = capacity;
            return this;
        }

        /**
         * Sets initial token count (defaults to tokenCapacity if not set).
         */
        public Builder initialTokens(double tokens) {
            if (tokens < 0) throw new IllegalArgumentException("initialTokens must be >= 0");
            this.initialTokens = tokens;
            return this;
        }

        public DualRateLimiter build() {
            return new DualRateLimiter(this);
        }
    }
}
