package com.msp.random;

import java.util.Objects;

/**
 * Immutable holder for an item and its associated positive weight.
 *
 * @param item   item instance
 * @param weight strictly positive numerical weight
 * @param <T>    item type
 */
public record WeightedItem<T>(T item, double weight) {

    public WeightedItem {
        Objects.requireNonNull(item, "item cannot be null");
        if (Double.isNaN(weight) || Double.isInfinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("Weight must be strictly positive (> 0.0), got: " + weight);
        }
    }
}
