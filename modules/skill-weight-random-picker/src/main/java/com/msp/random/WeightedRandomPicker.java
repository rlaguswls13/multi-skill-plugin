package com.msp.random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * High-performance weighted random item picker for A/B testing, lotteries, and drop rates.
 * <p>
 * Supports O(log N) cumulative distribution function (CDF) binary search selection
 * and weighted selection without replacement (Efraimidis-Spirakis A-Res algorithm).
 *
 * @param <T> item type
 */
public final class WeightedRandomPicker<T> {

    private final List<T> items;
    private final double[] weights;
    private final double[] cumulativeWeights;
    private final double totalWeight;
    private final Random random;

    private WeightedRandomPicker(List<WeightedItem<T>> weightedItems, Random random) {
        if (weightedItems.isEmpty()) {
            throw new IllegalStateException("Cannot build WeightedRandomPicker with no items");
        }
        this.random = Objects.requireNonNull(random, "Random cannot be null");

        int n = weightedItems.size();
        this.items = new ArrayList<>(n);
        this.weights = new double[n];
        this.cumulativeWeights = new double[n];

        double runningSum = 0.0;
        for (int i = 0; i < n; i++) {
            WeightedItem<T> wi = weightedItems.get(i);
            this.items.add(wi.item());
            this.weights[i] = wi.weight();
            runningSum += wi.weight();
            this.cumulativeWeights[i] = runningSum;
        }
        this.totalWeight = runningSum;
    }

    /**
     * Creates a new builder for constructing a WeightedRandomPicker.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Returns the number of distinct candidate items.
     */
    public int size() {
        return items.size();
    }

    /**
     * Total sum of all item weights.
     */
    public double getTotalWeight() {
        return totalWeight;
    }

    /**
     * Returns an unmodifiable view of all candidate items.
     */
    public List<T> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Picks a single item according to weighted probability in O(log N) time.
     *
     * @return selected item
     */
    public T pick() {
        double r;
        synchronized (random) {
            r = random.nextDouble() * totalWeight;
        }

        int idx = Arrays.binarySearch(cumulativeWeights, r);
        int itemIndex;
        if (idx >= 0) {
            itemIndex = Math.min(idx + 1, items.size() - 1);
        } else {
            itemIndex = -(idx + 1);
            if (itemIndex >= items.size()) {
                itemIndex = items.size() - 1;
            }
        }
        return items.get(itemIndex);
    }

    /**
     * Picks multiple items with replacement (independent trials).
     *
     * @param count number of items to pick
     * @return list of selected items
     */
    public List<T> pick(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative: " + count);
        }
        if (count == 0) {
            return List.of();
        }
        List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(pick());
        }
        return result;
    }

    /**
     * Picks multiple distinct items without replacement according to weights
     * using the Efraimidis-Spirakis A-Res reservoir algorithm.
     *
     * @param count number of unique items to pick (0 <= count <= size())
     * @return list of unique selected items
     */
    public List<T> pickWithoutReplacement(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative: " + count);
        }
        if (count > items.size()) {
            throw new IllegalArgumentException(
                    "Cannot pick " + count + " items without replacement from picker with size " + items.size()
            );
        }
        if (count == 0) {
            return List.of();
        }

        record KeyedItem<T>(T item, double key) {}

        List<KeyedItem<T>> keyedItems = new ArrayList<>(items.size());
        synchronized (random) {
            for (int i = 0; i < items.size(); i++) {
                double u = random.nextDouble();
                // avoid u = 0.0 to prevent Math.pow(0.0, ...) issues
                while (u <= 0.0) {
                    u = random.nextDouble();
                }
                // Key = u^(1 / weight)
                double key = Math.pow(u, 1.0 / weights[i]);
                keyedItems.add(new KeyedItem<>(items.get(i), key));
            }
        }

        // Sort descending by key
        keyedItems.sort(Comparator.comparingDouble((KeyedItem<T> k) -> k.key()).reversed());

        List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(keyedItems.get(i).item());
        }
        return result;
    }

    /**
     * Builder for {@link WeightedRandomPicker}.
     */
    public static final class Builder<T> {

        private final List<WeightedItem<T>> items = new ArrayList<>();
        private Random random = new Random();

        public Builder<T> add(T item, double weight) {
            items.add(new WeightedItem<>(item, weight));
            return this;
        }

        public Builder<T> addAll(Map<T, Double> itemWeights) {
            if (itemWeights != null) {
                for (Map.Entry<T, Double> entry : itemWeights.entrySet()) {
                    add(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        public Builder<T> random(Random random) {
            this.random = Objects.requireNonNull(random, "Random cannot be null");
            return this;
        }

        public WeightedRandomPicker<T> build() {
            return new WeightedRandomPicker<>(items, random);
        }
    }
}
