package com.msp.profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Node in a hierarchical profiling call tree representing a single timed step.
 */
public class ProfileNode {

    private final String name;
    private final ProfileNode parent;
    private final long startTimeNanos;
    private volatile long endTimeNanos = -1;
    private final List<ProfileNode> children = Collections.synchronizedList(new ArrayList<>());

    public ProfileNode(String name, ProfileNode parent) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.parent = parent;
        this.startTimeNanos = System.nanoTime();
    }

    public String getName() {
        return name;
    }

    public ProfileNode getParent() {
        return parent;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public long getEndTimeNanos() {
        return endTimeNanos;
    }

    public boolean isRunning() {
        return endTimeNanos < 0;
    }

    public void stop() {
        if (endTimeNanos < 0) {
            endTimeNanos = System.nanoTime();
        }
    }

    public long getElapsedNanos() {
        if (endTimeNanos >= 0) {
            return Math.max(0, endTimeNanos - startTimeNanos);
        }
        return Math.max(0, System.nanoTime() - startTimeNanos);
    }

    public double getElapsedMillis() {
        return getElapsedNanos() / 1_000_000.0;
    }

    public double getPercentageOf(long totalNanos) {
        if (totalNanos <= 0) {
            return 0.0;
        }
        return (getElapsedNanos() * 100.0) / totalNanos;
    }

    public List<ProfileNode> getChildren() {
        synchronized (children) {
            return new ArrayList<>(children);
        }
    }

    void addChild(ProfileNode child) {
        children.add(child);
    }

    @Override
    public String toString() {
        return String.format("[%s] %.2fms", name, getElapsedMillis());
    }
}
