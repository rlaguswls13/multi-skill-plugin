package com.msp.dedupwindow;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe sliding time window event deduplicator and TTL manager.
 * <p>
 * Designed for Kafka, RabbitMQ, and distributed event stream consumers
 * to reliably prevent duplicate message processing within a bounded sliding time window.
 *
 * @param <K> event key type (e.g., messageId, transactionId, eventHash)
 */
public class EventDeduplicator<K> implements AutoCloseable {

    private final long windowMillis;
    private final int maxCapacity;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<K, Long> cache;
    private final ScheduledExecutorService scheduler;
    private volatile boolean closed = false;

    /**
     * Creates an EventDeduplicator with specified window duration and maximum capacity.
     *
     * @param windowDuration time window during which duplicate events are detected
     * @param maxCapacity maximum number of unique keys retained in memory
     */
    public EventDeduplicator(Duration windowDuration, int maxCapacity) {
        this(windowDuration, maxCapacity, null, Clock.systemUTC());
    }

    /**
     * Creates an EventDeduplicator with a custom Clock (useful for unit testing).
     *
     * @param windowDuration time window during which duplicate events are detected
     * @param maxCapacity maximum number of unique keys retained in memory
     * @param clock custom clock for deterministic testing
     */
    public EventDeduplicator(Duration windowDuration, int maxCapacity, Clock clock) {
        this(windowDuration, maxCapacity, null, clock);
    }

    /**
     * Creates an EventDeduplicator with automatic background cleanup schedule.
     *
     * @param windowDuration time window during which duplicate events are detected
     * @param maxCapacity maximum number of unique keys retained in memory
     * @param cleanupInterval periodic background cleanup interval, or null for passive cleanup
     * @param clock time clock
     */
    public EventDeduplicator(Duration windowDuration, int maxCapacity, Duration cleanupInterval, Clock clock) {
        Objects.requireNonNull(windowDuration, "windowDuration cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        if (windowDuration.isNegative() || windowDuration.isZero()) {
            throw new IllegalArgumentException("windowDuration must be strictly positive: " + windowDuration);
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be strictly positive: " + maxCapacity);
        }

        this.windowMillis = windowDuration.toMillis();
        this.maxCapacity = maxCapacity;
        this.clock = clock;
        this.cache = new LinkedHashMap<>(Math.min(maxCapacity, 256), 0.75f, false);

        if (cleanupInterval != null && !cleanupInterval.isZero() && !cleanupInterval.isNegative()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "event-dedup-cleanup");
                t.setDaemon(true);
                return t;
            });
            long intervalMillis = cleanupInterval.toMillis();
            this.scheduler.scheduleAtFixedRate(this::cleanUp, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        } else {
            this.scheduler = null;
        }
    }

    /**
     * Checks if the given key has already been received within the sliding window.
     * If not duplicate, records the key with the current timestamp and returns {@code false}.
     * If already present and not expired, returns {@code true}.
     *
     * @param key event key
     * @return {@code true} if key is already present in active window; {@code false} if newly recorded
     */
    public boolean isDuplicate(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Event key cannot be null");
        }
        ensureOpen();

        lock.lock();
        try {
            long now = clock.millis();
            Long existingTime = cache.get(key);

            if (existingTime != null && (now - existingTime) <= windowMillis) {
                return true; // Already recorded within window
            }

            // Key is either new or expired; re-insert to refresh insertion position
            cache.remove(key);

            // If capacity limit reached, evict expired entries first
            if (cache.size() >= maxCapacity) {
                evictExpired(now);
            }

            // If still at capacity, evict the oldest entry (FIFO)
            if (cache.size() >= maxCapacity) {
                Iterator<K> iterator = cache.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }

            cache.put(key, now);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks whether the key exists in the active sliding window without registering it.
     *
     * @param key event key
     * @return {@code true} if present and not expired, {@code false} otherwise
     */
    public boolean checkOnly(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Event key cannot be null");
        }
        ensureOpen();

        lock.lock();
        try {
            long now = clock.millis();
            Long existingTime = cache.get(key);
            return existingTime != null && (now - existingTime) <= windowMillis;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the count of active (non-expired) event keys.
     *
     * @return number of unexpired keys currently tracked
     */
    public int size() {
        ensureOpen();
        lock.lock();
        try {
            evictExpired(clock.millis());
            return cache.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Manually triggers cleanup of expired event keys.
     */
    public void cleanUp() {
        if (closed) return;
        lock.lock();
        try {
            evictExpired(clock.millis());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears all recorded keys.
     */
    public void clear() {
        lock.lock();
        try {
            cache.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes this deduplicator, stopping any background scheduled tasks and clearing state.
     */
    @Override
    public void close() {
        closed = true;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        clear();
    }

    public Duration getWindowDuration() {
        return Duration.ofMillis(windowMillis);
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    private void evictExpired(long now) {
        long expireThreshold = now - windowMillis;
        Iterator<Map.Entry<K, Long>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, Long> entry = it.next();
            if (entry.getValue() < expireThreshold) {
                it.remove();
            } else {
                // Entries are ordered by timestamp ascending; stop early
                break;
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("EventDeduplicator has already been closed");
        }
    }
}
