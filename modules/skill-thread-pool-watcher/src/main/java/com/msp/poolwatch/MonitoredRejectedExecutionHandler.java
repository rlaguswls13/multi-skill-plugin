package com.msp.poolwatch;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Decorating {@link RejectedExecutionHandler} that counts task rejections and notifies listeners.
 */
public final class MonitoredRejectedExecutionHandler implements RejectedExecutionHandler {

    private final RejectedExecutionHandler delegate;
    private final AtomicLong rejectionCount = new AtomicLong(0);
    private final Consumer<Runnable> onRejectListener;

    public MonitoredRejectedExecutionHandler() {
        this(new ThreadPoolExecutor.AbortPolicy(), null);
    }

    public MonitoredRejectedExecutionHandler(RejectedExecutionHandler delegate) {
        this(delegate, null);
    }

    public MonitoredRejectedExecutionHandler(RejectedExecutionHandler delegate, Consumer<Runnable> onRejectListener) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.onRejectListener = onRejectListener;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        rejectionCount.incrementAndGet();
        if (onRejectListener != null) {
            try {
                onRejectListener.accept(r);
            } catch (Throwable ignored) {
            }
        }
        delegate.rejectedExecution(r, executor);
    }

    public long getRejectionCount() {
        return rejectionCount.get();
    }

    public void resetRejectionCount() {
        rejectionCount.set(0);
    }

    public RejectedExecutionHandler getDelegate() {
        return delegate;
    }
}
