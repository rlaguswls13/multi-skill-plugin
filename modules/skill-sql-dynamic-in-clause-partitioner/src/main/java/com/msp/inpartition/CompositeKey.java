package com.msp.inpartition;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a composite key composed of multiple ordered column values.
 *
 * @param values the ordered column values
 */
public record CompositeKey(List<Object> values) {

    public CompositeKey {
        Objects.requireNonNull(values, "values must not be null");
        values = Collections.unmodifiableList(List.copyOf(values));
    }

    /**
     * Creates a new CompositeKey from variable arguments.
     *
     * @param values values of the composite key
     * @return CompositeKey instance
     */
    public static CompositeKey of(Object... values) {
        Objects.requireNonNull(values, "values array must not be null");
        return new CompositeKey(Arrays.asList(values));
    }

    /**
     * Creates a new CompositeKey from a list of values.
     *
     * @param values list of values
     * @return CompositeKey instance
     */
    public static CompositeKey from(List<?> values) {
        Objects.requireNonNull(values, "values list must not be null");
        return new CompositeKey(List.copyOf(values));
    }

    /**
     * Returns the number of columns in this composite key.
     */
    public int size() {
        return values.size();
    }
}
