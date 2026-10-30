package com.msp.objpool;

/**
 * 풀에서 빌린 객체의 AutoCloseable 래퍼.
 * try-with-resources 종료 시 자동으로 풀에 반납됩니다.
 */
public final class Pooled<T> implements AutoCloseable {

    private final ObjectPool<T> pool;
    private final T object;
    private boolean closed = false;

    public Pooled(ObjectPool<T> pool, T object) {
        this.pool = pool;
        this.object = object;
    }

    public T get() {
        if (closed) {
            throw new IllegalStateException("Object has already been returned to pool");
        }
        return object;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            pool.release(object);
        }
    }
}
