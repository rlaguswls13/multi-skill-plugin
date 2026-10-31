package com.msp.slidingcounter;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.LongSupplier;

/**
 * High-throughput low-memory sliding window counter rate limiter.
 * Splits a fixed time window into M sub-slots arranged as a circular atomic ring.
 * Features O(1) time complexity, minimal memory footprint compared to sliding log,
 * and lock-free concurrency.
 */
public final class SlidingCounterRateLimiter {

    private final long maxRequests;
    private final Duration windowDuration;
    private final int slotCount;
    private final long slotDurationMs;
    private final LongSupplier timeSupplier;

    private record SlotState(long slotId, long count) {}

    private final AtomicReferenceArray<SlotState> ring;
    private final AtomicLong totalPermits = new AtomicLong(0L);

    /**
     * Constructs a sliding counter rate limiter using the system clock.
     *
     * @param maxRequests maximum allowed requests within the window
     * @param windowDuration duration of the sliding window
     * @param slotCount number of sub-slots to split the window into
     */
    public SlidingCounterRateLimiter(long maxRequests, Duration windowDuration, int slotCount) {
        this(maxRequests, windowDuration, slotCount, System::currentTimeMillis);
    }

    /**
     * Constructs a sliding counter rate limiter with a custom time supplier (useful for deterministic testing).
     *
     * @param maxRequests maximum allowed requests within the window
     * @param windowDuration duration of the sliding window
     * @param slotCount number of sub-slots to split the window into
     * @param timeSupplier supplier of epoch milliseconds
     */
    public SlidingCounterRateLimiter(long maxRequests, Duration windowDuration, int slotCount, LongSupplier timeSupplier) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive, got: " + maxRequests);
        }
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        if (windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("windowDuration must be positive, got: " + windowDuration);
        }
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive, got: " + slotCount);
        }

        long windowMs = windowDuration.toMillis();
        long slotMs = windowMs / slotCount;
        if (slotMs <= 0) {
            throw new IllegalArgumentException("windowDuration (" + windowMs + " ms) must be at least slotCount (" + slotCount + ") ms");
        }

        this.maxRequests = maxRequests;
        this.windowDuration = windowDuration;
        this.slotCount = slotCount;
        this.slotDurationMs = slotMs;
        this.timeSupplier = Objects.requireNonNull(timeSupplier, "timeSupplier must not be null");

        this.ring = new AtomicReferenceArray<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            this.ring.set(i, new SlotState(-1L, 0L));
        }
    }

    /**
     * Attempts to acquire 1 permit.
     *
     * @return true if permit acquired, false if rate limit exceeded
     */
    public boolean tryAcquire() {
        return tryAcquire(1L);
    }

    /**
     * Attempts to acquire the specified number of permits.
     *
     * @param permits number of permits to acquire (must be > 0)
     * @return true if permits acquired, false if rate limit exceeded
     */
    public boolean tryAcquire(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive, got: " + permits);
        }
        if (permits > maxRequests) {
            return false;
        }

        while (true) {
            long now = timeSupplier.getAsLong();
            long currentSlotId = now / slotDurationMs;
            int ringIndex = (int) Math.floorMod(currentSlotId, slotCount);

            // 1. Clean any expired slots across the ring
            cleanExpiredSlots(currentSlotId);

            // 2. Prepare slot at ringIndex
            SlotState cur = ring.get(ringIndex);
            if (cur != null && cur.slotId() != currentSlotId) {
                if (cur.slotId() >= 0 && cur.count() > 0) {
                    if (ring.compareAndSet(ringIndex, cur, new SlotState(currentSlotId, 0L))) {
                        totalPermits.addAndGet(-cur.count());
                    } else {
                        continue;
                    }
                } else {
                    ring.compareAndSet(ringIndex, cur, new SlotState(currentSlotId, 0L));
                }
            }

            // 3. Check and atomically reserve permits
            long currentTotal = totalPermits.get();
            if (currentTotal + permits > maxRequests) {
                cleanExpiredSlots(currentSlotId);
                currentTotal = totalPermits.get();
                if (currentTotal + permits > maxRequests) {
                    return false;
                }
            }

            if (!totalPermits.compareAndSet(currentTotal, currentTotal + permits)) {
                continue;
            }

            // 4. Update slot count in the circular ring
            while (true) {
                SlotState s = ring.get(ringIndex);
                if (s != null && s.slotId() == currentSlotId) {
                    SlotState next = new SlotState(currentSlotId, s.count() + permits);
                    if (ring.compareAndSet(ringIndex, s, next)) {
                        return true;
                    }
                } else {
                    SlotState next = new SlotState(currentSlotId, permits);
                    if (ring.compareAndSet(ringIndex, s, next)) {
                        return true;
                    }
                }
            }
        }
    }

    /**
     * Returns the aggregate count of requests within the current sliding window.
     */
    public long getCurrentCount() {
        long now = timeSupplier.getAsLong();
        long currentSlotId = now / slotDurationMs;
        cleanExpiredSlots(currentSlotId);
        return Math.max(0L, totalPermits.get());
    }

    private void cleanExpiredSlots(long currentSlotId) {
        for (int i = 0; i < slotCount; i++) {
            SlotState s = ring.get(i);
            if (s != null && s.slotId() >= 0 && s.slotId() <= currentSlotId - slotCount) {
                if (ring.compareAndSet(i, s, new SlotState(-1L, 0L))) {
                    totalPermits.addAndGet(-s.count());
                }
            }
        }
    }

    /**
     * Resets all slots and counters in the sliding counter.
     */
    public void reset() {
        for (int i = 0; i < slotCount; i++) {
            ring.set(i, new SlotState(-1L, 0L));
        }
        totalPermits.set(0L);
    }

    public long getMaxRequests() {
        return maxRequests;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public long getSlotDurationMs() {
        return slotDurationMs;
    }
}
