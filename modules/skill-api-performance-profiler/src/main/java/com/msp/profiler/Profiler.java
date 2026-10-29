package com.msp.profiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Hierarchical API call tree performance profiler with sub-millisecond nanosecond accuracy,
 * percentage breakdown of execution times, and ASCII/Unicode call-tree pretty printers.
 */
public class Profiler {

    private static final ThreadLocal<Profiler> THREAD_LOCAL_PROFILER = new ThreadLocal<>();

    private final ProfileNode root;
    private final Deque<ProfileNode> nodeStack = new ArrayDeque<>();
    private volatile boolean stopped = false;

    private Profiler(String rootName) {
        this.root = new ProfileNode(rootName, null);
        this.nodeStack.push(root);
    }

    /**
     * Creates and starts a new standalone Profiler instance.
     *
     * @param rootName root operation name
     * @return profiler instance
     */
    public static Profiler start(String rootName) {
        return new Profiler(rootName);
    }

    /**
     * Starts a thread-local trace for the current thread.
     */
    public static Profiler startTrace(String rootName) {
        Profiler profiler = new Profiler(rootName);
        THREAD_LOCAL_PROFILER.set(profiler);
        return profiler;
    }

    /**
     * Enters a child profiling step in the current thread-local trace.
     */
    public static ProfileScope enter(String stepName) {
        Profiler current = THREAD_LOCAL_PROFILER.get();
        if (current == null) {
            current = startTrace("Trace");
        }
        return current.step(stepName);
    }

    /**
     * Stops the thread-local trace and returns the root ProfileNode.
     */
    public static ProfileNode stopTrace() {
        Profiler current = THREAD_LOCAL_PROFILER.get();
        if (current != null) {
            current.stop();
            THREAD_LOCAL_PROFILER.remove();
            return current.getRoot();
        }
        return null;
    }

    /**
     * Gets the active thread-local Profiler if any.
     */
    public static Optional<Profiler> current() {
        return Optional.ofNullable(THREAD_LOCAL_PROFILER.get());
    }

    /**
     * Prints the current thread-local trace tree.
     */
    public static String printCurrentTrace() {
        Profiler current = THREAD_LOCAL_PROFILER.get();
        return current != null ? current.printTree() : "";
    }

    /**
     * Enters a nested profiling step. Automatically becomes a child of the current active step.
     * Use with try-with-resources:
     * <pre>{@code
     * try (ProfileScope scope = profiler.step("dbQuery")) {
     *     // work
     * }
     * }</pre>
     *
     * @param stepName name of the step
     * @return ProfileScope to close upon step completion
     */
    public synchronized ProfileScope step(String stepName) {
        Objects.requireNonNull(stepName, "stepName must not be null");
        ProfileNode parent = nodeStack.peek();
        if (parent == null) {
            parent = root;
        }

        ProfileNode child = new ProfileNode(stepName, parent);
        parent.addChild(child);
        nodeStack.push(child);

        return new ProfileScope(child, () -> {
            synchronized (Profiler.this) {
                if (!nodeStack.isEmpty() && nodeStack.peek() == child) {
                    nodeStack.pop();
                } else {
                    nodeStack.remove(child);
                }
            }
        });
    }

    /**
     * Stops the profiler root node.
     */
    public synchronized void stop() {
        if (!stopped) {
            stopped = true;
            while (!nodeStack.isEmpty()) {
                ProfileNode node = nodeStack.pop();
                node.stop();
            }
            root.stop();
        }
    }

    public ProfileNode getRoot() {
        return root;
    }

    public long getTotalElapsedNanos() {
        return root.getElapsedNanos();
    }

    public double getTotalElapsedMillis() {
        return root.getElapsedMillis();
    }

    /**
     * Pretty-prints the call tree using Unicode box-drawing characters.
     */
    public String printTree() {
        return printTree(false);
    }

    /**
     * Pretty-prints the call tree using pure ASCII (+--, |--, \--) characters.
     */
    public String printAsciiTree() {
        return printTree(true);
    }

    /**
     * Pretty-prints the hierarchical call tree.
     *
     * @param asciiOnly if true, uses only standard 7-bit ASCII characters
     * @return formatted tree string
     */
    public synchronized String printTree(boolean asciiOnly) {
        long rootTotal = root.getElapsedNanos();
        StringBuilder sb = new StringBuilder();

        // Print root
        sb.append(String.format("[%s] %.2fms (100.0%%)%n", root.getName(), root.getElapsedMillis()));

        List<ProfileNode> children = root.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean isLast = (i == children.size() - 1);
            printNode(sb, children.get(i), "", isLast, rootTotal, asciiOnly);
        }

        return sb.toString().stripTrailing();
    }

    private void printNode(StringBuilder sb, ProfileNode node, String prefix, boolean isLast, long rootTotal, boolean asciiOnly) {
        String connector;
        if (asciiOnly) {
            connector = isLast ? "\\-- " : "+-- ";
        } else {
            connector = isLast ? "└── " : "├── ";
        }

        double percent = node.getPercentageOf(rootTotal);
        sb.append(prefix)
                .append(connector)
                .append(String.format("[%s] %.2fms (%.1f%%)%n", node.getName(), node.getElapsedMillis(), percent));

        String childPrefix;
        if (asciiOnly) {
            childPrefix = prefix + (isLast ? "    " : "|   ");
        } else {
            childPrefix = prefix + (isLast ? "    " : "│   ");
        }

        List<ProfileNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean lastChild = (i == children.size() - 1);
            printNode(sb, children.get(i), childPrefix, lastChild, rootTotal, asciiOnly);
        }
    }
}
