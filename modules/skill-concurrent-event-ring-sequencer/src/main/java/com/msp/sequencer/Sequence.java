package com.msp.sequencer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Cache-line padded 64-bit sequence counter to prevent false sharing in lock-free ring buffers.
 * Uses VarHandle for acquire/release and compare-and-set semantics.
 */
public class Sequence {

    public static final long INITIAL_VALUE = -1L;

    // Cache line padding before
    protected long p1, p2, p3, p4, p5, p6, p7;

    // The sequence value
    protected volatile long value;

    // Cache line padding after
    protected long p8, p9, p10, p11, p12, p13, p14;

    private static final VarHandle VALUE_VH;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            VALUE_VH = lookup.findVarHandle(Sequence.class, "value", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public Sequence() {
        this(INITIAL_VALUE);
    }

    public Sequence(long initialValue) {
        VALUE_VH.setVolatile(this, initialValue);
    }

    public long get() {
        return value;
    }

    public long getAcquire() {
        return (long) VALUE_VH.getAcquire(this);
    }

    public void set(long value) {
        VALUE_VH.setVolatile(this, value);
    }

    public void setRelease(long value) {
        VALUE_VH.setRelease(this, value);
    }

    public boolean compareAndSet(long expectedValue, long newValue) {
        return VALUE_VH.compareAndSet(this, expectedValue, newValue);
    }

    public long incrementAndGet() {
        return (long) VALUE_VH.getAndAdd(this, 1L) + 1L;
    }

    public long addAndGet(long delta) {
        return (long) VALUE_VH.getAndAdd(this, delta) + delta;
    }

    @Override
    public String toString() {
        return Long.toString(get());
    }
}
