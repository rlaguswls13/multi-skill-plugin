package com.msp.objpool;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 고비용 객체(버퍼, 파서, 소켓 등)의 빈번한 생성과 GC 부하를 최소화하는
 * 세마포어 & ConcurrentLinkedQueue 기반 고성능 무잠금(Lock-Free) 인메모리 오브젝트 풀.
 */
public final class ObjectPool<T> implements AutoCloseable {

    private final Supplier<T> factory;
    private final Consumer<T> resetHandler;
    private final int maxSize;
    private final Semaphore semaphore;
    private final ConcurrentLinkedQueue<T> pool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger createdCount = new AtomicInteger(0);

    private ObjectPool(Builder<T> builder) {
        this.factory = Objects.requireNonNull(builder.factory, "factory must not be null");
        this.resetHandler = builder.resetHandler;
        this.maxSize = builder.maxSize;
        this.semaphore = new Semaphore(builder.maxSize, true);
    }

    public static <T> Builder<T> builder(Supplier<T> factory) {
        return new Builder<>(factory);
    }

    public Pooled<T> borrow() {
        return borrow(Duration.ofSeconds(5));
    }

    public Pooled<T> borrow(Duration timeout) {
        try {
            boolean acquired = semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new TimeoutException("Failed to acquire object from pool within " + timeout);
            }

            T obj = pool.poll();
            if (obj == null) {
                obj = factory.get();
                createdCount.incrementAndGet();
            }
            return new Pooled<>(this, obj);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while waiting for pool object", e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public void release(T obj) {
        if (obj == null) return;
        if (resetHandler != null) {
            try {
                resetHandler.accept(obj);
            } catch (Exception ignored) {}
        }
        pool.offer(obj);
        semaphore.release();
    }

    public <R> R withObject(Function<T, R> action) {
        try (Pooled<T> pooled = borrow()) {
            return action.apply(pooled.get());
        }
    }

    public int idleCount() {
        return pool.size();
    }

    public int activeCount() {
        return maxSize - semaphore.availablePermits();
    }

    public int maxSize() {
        return maxSize;
    }

    @Override
    public void close() {
        pool.clear();
    }

    public static final class Builder<T> {
        private final Supplier<T> factory;
        private Consumer<T> resetHandler = null;
        private int maxSize = 16;

        private Builder(Supplier<T> factory) {
            this.factory = factory;
        }

        public Builder<T> reset(Consumer<T> resetHandler) {
            this.resetHandler = resetHandler;
            return this;
        }

        public Builder<T> maxSize(int maxSize) {
            if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive: " + maxSize);
            this.maxSize = maxSize;
            return this;
        }

        public ObjectPool<T> build() {
            return new ObjectPool<>(this);
        }
    }
}
