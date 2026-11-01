package com.msp.eventdedup;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, low-memory Striped-Locking + LRU sliding window idempotency event deduplicator
 * for high-throughput distributed streams.
 */
public class EventDeduplicator {

    /**
     * Processing status of a registered event.
     */
    public enum EventStatus {
        IN_FLIGHT,
        PROCESSED
    }

    private static final class EventRecord {
        private final long createdNanos;
        private volatile EventStatus status;

        EventRecord(long createdNanos, EventStatus status) {
            this.createdNanos = createdNanos;
            this.status = status;
        }

        long createdNanos() {
            return createdNanos;
        }

        EventStatus status() {
            return status;
        }

        void setStatus(EventStatus status) {
            this.status = status;
        }
    }

    private static final class Segment {
        private final ReentrantLock lock = new ReentrantLock();
        private final LinkedHashMap<String, EventRecord> map;

        Segment(int segmentCapacity) {
            this.map = new LinkedHashMap<String, EventRecord>(segmentCapacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, EventRecord> eldest) {
                    return size() > segmentCapacity;
                }
            };
        }
    }

    private final Segment[] segments;
    private final int mask;
    private final long ttlNanos;
    private final int capacity;

    /**
     * Constructs an {@link EventDeduplicator} with the given total capacity, TTL, and concurrency level.
     *
     * @param capacity         the maximum total number of entries cached across all segments
     * @param ttl              the time-to-live duration before an event ID is considered expired
     * @param concurrencyLevel the number of lock stripes/segments (rounded up to nearest power of 2)
     */
    public EventDeduplicator(int capacity, Duration ttl, int concurrencyLevel) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be strictly positive: " + ttl);
        }
        if (concurrencyLevel <= 0) {
            throw new IllegalArgumentException("concurrencyLevel must be positive: " + concurrencyLevel);
        }

        this.capacity = capacity;
        this.ttlNanos = ttl.toNanos();

        int numSegments = 1;
        while (numSegments < concurrencyLevel) {
            numSegments <<= 1;
        }
        this.mask = numSegments - 1;
        this.segments = new Segment[numSegments];

        int perSegmentCapacity = Math.max(1, (capacity + numSegments - 1) / numSegments);
        for (int i = 0; i < numSegments; i++) {
            this.segments[i] = new Segment(perSegmentCapacity);
        }
    }

    /**
     * Constructs an {@link EventDeduplicator} with the default concurrency level of 16.
     *
     * @param capacity the maximum total number of entries
     * @param ttl      the time-to-live duration
     */
    public EventDeduplicator(int capacity, Duration ttl) {
        this(capacity, ttl, 16);
    }

    /**
     * Tries to acquire and register the given event ID.
     *
     * @param eventId the unique event identifier
     * @return {@code true} if the event was acquired (unique and registered as IN_FLIGHT);
     *         {@code false} if already in flight or duplicate within TTL
     */
    public boolean tryAcquire(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Segment seg = getSegment(eventId);
        long now = System.nanoTime();

        seg.lock.lock();
        try {
            EventRecord existing = seg.map.get(eventId);
            if (existing != null) {
                if (now - existing.createdNanos() < ttlNanos) {
                    return false; // active duplicate or in-flight
                }
            }
            seg.map.put(eventId, new EventRecord(now, EventStatus.IN_FLIGHT));
            return true;
        } finally {
            seg.lock.unlock();
        }
    }

    /**
     * Checks whether the event is currently registered and not expired.
     *
     * @param eventId the unique event identifier
     * @return {@code true} if the event is a duplicate within TTL; {@code false} otherwise
     */
    public boolean isDuplicate(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Segment seg = getSegment(eventId);
        long now = System.nanoTime();

        seg.lock.lock();
        try {
            EventRecord existing = seg.map.get(eventId);
            if (existing == null) {
                return false;
            }
            if (now - existing.createdNanos() < ttlNanos) {
                return true;
            }
            // expired: remove lazily
            seg.map.remove(eventId);
            return false;
        } finally {
            seg.lock.unlock();
        }
    }

    /**
     * Marks an in-flight event as successfully completed.
     *
     * @param eventId the unique event identifier
     */
    public void markProcessed(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Segment seg = getSegment(eventId);

        seg.lock.lock();
        try {
            EventRecord existing = seg.map.get(eventId);
            if (existing != null) {
                existing.setStatus(EventStatus.PROCESSED);
            }
        } finally {
            seg.lock.unlock();
        }
    }

    /**
     * Releases an in-flight event if processing failed, allowing retry.
     * If the event was already marked as PROCESSED, it is retained.
     *
     * @param eventId the unique event identifier
     */
    public void release(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Segment seg = getSegment(eventId);

        seg.lock.lock();
        try {
            EventRecord existing = seg.map.get(eventId);
            if (existing != null && existing.status() == EventStatus.IN_FLIGHT) {
                seg.map.remove(eventId);
            }
        } finally {
            seg.lock.unlock();
        }
    }

    /**
     * Scans and purges all expired entries from all segments.
     *
     * @return the number of expired entries removed
     */
    public int cleanupExpired() {
        long now = System.nanoTime();
        int totalRemoved = 0;

        for (Segment seg : segments) {
            seg.lock.lock();
            try {
                Iterator<Map.Entry<String, EventRecord>> it = seg.map.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, EventRecord> entry = it.next();
                    if (now - entry.getValue().createdNanos() >= ttlNanos) {
                        it.remove();
                        totalRemoved++;
                    }
                }
            } finally {
                seg.lock.unlock();
            }
        }

        return totalRemoved;
    }

    /**
     * Returns the total number of non-expired entries currently retained across all segments.
     *
     * @return current active entry count
     */
    public int size() {
        long now = System.nanoTime();
        int count = 0;

        for (Segment seg : segments) {
            seg.lock.lock();
            try {
                for (EventRecord record : seg.map.values()) {
                    if (now - record.createdNanos() < ttlNanos) {
                        count++;
                    }
                }
            } finally {
                seg.lock.unlock();
            }
        }

        return count;
    }

    /**
     * Clears all entries across all segments.
     */
    public void clear() {
        for (Segment seg : segments) {
            seg.lock.lock();
            try {
                seg.map.clear();
            } finally {
                seg.lock.unlock();
            }
        }
    }

    /**
     * Returns the configured total capacity.
     */
    public int capacity() {
        return capacity;
    }

    private Segment getSegment(String eventId) {
        int h = spread(eventId.hashCode());
        return segments[h & mask];
    }

    private static int spread(int h) {
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }
}
