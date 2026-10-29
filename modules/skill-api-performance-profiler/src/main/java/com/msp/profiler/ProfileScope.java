package com.msp.profiler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AutoCloseable scope that automatically stops timing a ProfileNode upon exit.
 */
public class ProfileScope implements AutoCloseable {

    private final ProfileNode node;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ProfileScope(ProfileNode node, Runnable onClose) {
        this.node = Objects.requireNonNull(node, "node must not be null");
        this.onClose = onClose != null ? onClose : () -> {};
    }

    public ProfileNode getNode() {
        return node;
    }

    public String getName() {
        return node.getName();
    }

    public double getElapsedMillis() {
        return node.getElapsedMillis();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            node.stop();
            onClose.run();
        }
    }
}
