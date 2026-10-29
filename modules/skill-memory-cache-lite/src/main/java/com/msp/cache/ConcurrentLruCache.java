package com.msp.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * High-performance thread-safe Concurrent LRU Cache supporting maximum capacity,
 * per-entry TTL expiration, and detailed hit/miss metrics.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class ConcurrentLruCache<K, V> implements MemoryCache<K, V> {

    private static final class Node<K, V> {
        final K key;
        volatile V value;
        volatile long expireAtNano;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value, long expireAtNano) {
            this.key = key;
            this.value = value;
            this.expireAtNano = expireAtNano;
        }

        boolean isExpired(long now) {
            return expireAtNano != Long.MAX_VALUE && now >= expireAtNano;
        }
    }

    private final int maxCapacity;
    private final Duration defaultTtl;
    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final ReentrantLock lock = new ReentrantLock();

    private Node<K, V> head;
    private Node<K, V> tail;

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong expirationCount = new AtomicLong();

    public ConcurrentLruCache(int maxCapacity) {
        this(maxCapacity, null);
    }

    public ConcurrentLruCache(int maxCapacity, Duration defaultTtl) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be positive: " + maxCapacity);
        }
        if (defaultTtl != null && (defaultTtl.isNegative() || defaultTtl.isZero())) {
            throw new IllegalArgumentException("defaultTtl must be positive: " + defaultTtl);
        }
        this.maxCapacity = maxCapacity;
        this.defaultTtl = defaultTtl;
        this.map = new ConcurrentHashMap<>(maxCapacity);
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key cannot be null");

        Node<K, V> node = map.get(key);
        if (node == null) {
            missCount.incrementAndGet();
            return Optional.empty();
        }

        long now = System.nanoTime();
        if (node.isExpired(now)) {
            missCount.incrementAndGet();
            expirationCount.incrementAndGet();
            lock.lock();
            try {
                if (map.remove(key, node)) {
                    unlink(node);
                }
            } finally {
                lock.unlock();
            }
            return Optional.empty();
        }

        hitCount.incrementAndGet();
        lock.lock();
        try {
            if (node.prev != null || node.next != null || head == node) {
                moveToHead(node);
            }
        } finally {
            lock.unlock();
        }

        return Optional.ofNullable(node.value);
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultTtl);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        Duration effectiveTtl = ttl != null ? ttl : defaultTtl;
        long expireAtNano = (effectiveTtl != null && !effectiveTtl.isZero())
                ? System.nanoTime() + effectiveTtl.toNanos()
                : Long.MAX_VALUE;

        lock.lock();
        try {
            putInternal(key, value, expireAtNano);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V computeIfAbsent(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(mappingFunction, "mappingFunction cannot be null");

        Optional<V> existing = get(key);
        if (existing.isPresent()) {
            return existing.get();
        }

        Duration effectiveTtl = ttl != null ? ttl : defaultTtl;
        long expireAtNano = (effectiveTtl != null && !effectiveTtl.isZero())
                ? System.nanoTime() + effectiveTtl.toNanos()
                : Long.MAX_VALUE;

        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            long now = System.nanoTime();
            if (node != null && !node.isExpired(now)) {
                hitCount.incrementAndGet();
                moveToHead(node);
                return node.value;
            }

            V computed = mappingFunction.apply(key);
            if (computed != null) {
                putInternal(key, computed, expireAtNano);
            }
            return computed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean invalidate(K key) {
        Objects.requireNonNull(key, "key cannot be null");
        lock.lock();
        try {
            Node<K, V> removed = map.remove(key);
            if (removed != null) {
                unlink(removed);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            map.clear();
            head = null;
            tail = null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public CacheStats stats() {
        return new CacheStats(
                hitCount.get(),
                missCount.get(),
                evictionCount.get(),
                expirationCount.get()
        );
    }

    @Override
    public int cleanUpExpired() {
        long now = System.nanoTime();
        int cleaned = 0;

        for (Node<K, V> node : map.values()) {
            if (node.isExpired(now)) {
                lock.lock();
                try {
                    if (map.remove(node.key, node)) {
                        unlink(node);
                        expirationCount.incrementAndGet();
                        cleaned++;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        return cleaned;
    }

    public int maxCapacity() {
        return maxCapacity;
    }

    public Duration defaultTtl() {
        return defaultTtl;
    }

    private void putInternal(K key, V value, long expireAtNano) {
        Node<K, V> existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            existing.expireAtNano = expireAtNano;
            moveToHead(existing);
        } else {
            while (map.size() >= maxCapacity && tail != null) {
                Node<K, V> toEvict = tail;
                unlink(toEvict);
                map.remove(toEvict.key);
                evictionCount.incrementAndGet();
            }
            Node<K, V> newNode = new Node<>(key, value, expireAtNano);
            insertAtHead(newNode);
            map.put(key, newNode);
        }
    }

    private void insertAtHead(Node<K, V> node) {
        node.prev = null;
        node.next = head;
        if (head != null) {
            head.prev = node;
        }
        head = node;
        if (tail == null) {
            tail = node;
        }
    }

    private void unlink(Node<K, V> node) {
        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            head = node.next;
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        } else {
            tail = node.prev;
        }
        node.prev = null;
        node.next = null;
    }

    private void moveToHead(Node<K, V> node) {
        if (head == node) {
            return;
        }
        unlink(node);
        insertAtHead(node);
    }
}
