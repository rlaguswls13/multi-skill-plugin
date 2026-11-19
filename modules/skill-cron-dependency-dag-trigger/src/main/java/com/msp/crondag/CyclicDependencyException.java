package com.msp.crondag;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a cycle is detected within task dependency definitions.
 */
public class CyclicDependencyException extends RuntimeException {

    private final List<String> cyclePath;

    public CyclicDependencyException(List<String> cyclePath) {
        super("Cyclic dependency detected in task DAG: " + String.join(" -> ", cyclePath));
        this.cyclePath = cyclePath != null ? Collections.unmodifiableList(cyclePath) : List.of();
    }

    public List<String> getCyclePath() {
        return cyclePath;
    }
}
