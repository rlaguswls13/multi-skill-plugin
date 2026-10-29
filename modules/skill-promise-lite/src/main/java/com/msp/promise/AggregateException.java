package com.msp.promise;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when all promises in an {@code anyOf} collection are rejected.
 */
public class AggregateException extends RuntimeException {

    private final List<Throwable> causes;

    public AggregateException(String message, List<Throwable> causes) {
        super(message);
        this.causes = (causes != null) ? List.copyOf(causes) : Collections.emptyList();
    }

    public List<Throwable> getCauses() {
        return causes;
    }
}
