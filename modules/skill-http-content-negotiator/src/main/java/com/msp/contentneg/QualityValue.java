package com.msp.contentneg;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Quality-value weighted item according to RFC 7231 section 5.3.1.
 *
 * @param value      The item value (e.g. "text/html", "en-US", "gzip")
 * @param quality    The quality factor (q-value), ranging from 0.0 to 1.0 (default 1.0)
 * @param parameters Additional item parameters (e.g. "charset", "level")
 * @param <T>        Type of the item value
 */
public record QualityValue<T>(
        T value,
        double quality,
        Map<String, String> parameters
) implements Comparable<QualityValue<T>> {

    public QualityValue {
        Objects.requireNonNull(value, "value must not be null");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        if (quality < 0.0 || quality > 1.0) {
            throw new IllegalArgumentException("Quality factor must be between 0.0 and 1.0: " + quality);
        }
    }

    public QualityValue(T value, double quality) {
        this(value, quality, Map.of());
    }

    public QualityValue(T value) {
        this(value, 1.0, Map.of());
    }

    @Override
    public int compareTo(QualityValue<T> other) {
        // Descending order of quality factor
        return Double.compare(other.quality, this.quality);
    }
}
