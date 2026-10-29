package com.msp.random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class WeightedRandomPickerTest {

    @Nested
    @DisplayName("Validation & Construction Tests")
    class ValidationTests {

        @Test
        @DisplayName("Zero or negative weight throws IllegalArgumentException")
        void testZeroOrNegativeWeight() {
            assertThrows(IllegalArgumentException.class, () -> new WeightedItem<>("A", 0.0));
            assertThrows(IllegalArgumentException.class, () -> new WeightedItem<>("A", -1.5));
            assertThrows(IllegalArgumentException.class, () -> new WeightedItem<>("A", Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> new WeightedItem<>("A", Double.POSITIVE_INFINITY));

            assertThrows(IllegalArgumentException.class, () ->
                    WeightedRandomPicker.<String>builder().add("A", 0.0)
            );
        }

        @Test
        @DisplayName("Null item throws NullPointerException")
        void testNullItem() {
            assertThrows(NullPointerException.class, () -> new WeightedItem<>(null, 1.0));
            assertThrows(NullPointerException.class, () ->
                    WeightedRandomPicker.<String>builder().add(null, 1.0)
            );
        }

        @Test
        @DisplayName("Empty builder throws IllegalStateException on build()")
        void testEmptyBuilder() {
            WeightedRandomPicker.Builder<String> builder = WeightedRandomPicker.builder();
            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        @DisplayName("Single item picker always returns that item")
        void testSingleItemPicker() {
            WeightedRandomPicker<String> picker = WeightedRandomPicker.<String>builder()
                    .add("SOLO", 5.0)
                    .build();

            assertEquals(1, picker.size());
            assertEquals(5.0, picker.getTotalWeight());
            for (int i = 0; i < 50; i++) {
                assertEquals("SOLO", picker.pick());
            }
        }
    }

    @Nested
    @DisplayName("Statistical Distribution Tests")
    class StatisticalTests {

        @Test
        @DisplayName("Distribution over 10,000 iterations conforms to 70:20:10 ratio within tolerance")
        void testDistributionRatio() {
            // Seeded random for reproducible tests
            Random random = new Random(42);
            WeightedRandomPicker<String> picker = WeightedRandomPicker.<String>builder()
                    .random(random)
                    .add("A", 70.0)
                    .add("B", 20.0)
                    .add("C", 10.0)
                    .build();

            int iterations = 10_000;
            Map<String, Integer> counts = new HashMap<>();
            counts.put("A", 0);
            counts.put("B", 0);
            counts.put("C", 0);

            for (int i = 0; i < iterations; i++) {
                String picked = picker.pick();
                counts.put(picked, counts.get(picked) + 1);
            }

            double aRatio = counts.get("A") / (double) iterations;
            double bRatio = counts.get("B") / (double) iterations;
            double cRatio = counts.get("C") / (double) iterations;

            // Expected: A ~ 0.70, B ~ 0.20, C ~ 0.10
            // Tolerance ±0.02 (2%)
            assertEquals(0.70, aRatio, 0.02, "Ratio for A should be close to 70%");
            assertEquals(0.20, bRatio, 0.02, "Ratio for B should be close to 20%");
            assertEquals(0.10, cRatio, 0.02, "Ratio for C should be close to 10%");
        }

        @Test
        @DisplayName("pick(count) returns exact number of samples")
        void testPickMultiple() {
            WeightedRandomPicker<String> picker = WeightedRandomPicker.<String>builder()
                    .add("item1", 1.0)
                    .add("item2", 1.0)
                    .build();

            assertEquals(List.of(), picker.pick(0));
            assertThrows(IllegalArgumentException.class, () -> picker.pick(-1));

            List<String> picked = picker.pick(100);
            assertEquals(100, picked.size());
        }
    }

    @Nested
    @DisplayName("Sampling Without Replacement Tests")
    class WithoutReplacementTests {

        @Test
        @DisplayName("pickWithoutReplacement returns unique elements")
        void testUniqueness() {
            WeightedRandomPicker<String> picker = WeightedRandomPicker.<String>builder()
                    .add("Bronze", 50.0)
                    .add("Silver", 30.0)
                    .add("Gold", 15.0)
                    .add("Diamond", 5.0)
                    .build();

            for (int trial = 0; trial < 100; trial++) {
                List<String> picked = picker.pickWithoutReplacement(3);
                assertEquals(3, picked.size());

                Set<String> uniqueSet = new HashSet<>(picked);
                assertEquals(3, uniqueSet.size(), "Selected items must be unique");
            }

            // Picking all items
            List<String> allPicked = picker.pickWithoutReplacement(4);
            assertEquals(4, allPicked.size());
            assertEquals(Set.of("Bronze", "Silver", "Gold", "Diamond"), new HashSet<>(allPicked));
        }

        @Test
        @DisplayName("pickWithoutReplacement bounds and error handling")
        void testWithoutReplacementErrors() {
            WeightedRandomPicker<String> picker = WeightedRandomPicker.<String>builder()
                    .add("A", 1.0)
                    .add("B", 1.0)
                    .build();

            assertEquals(List.of(), picker.pickWithoutReplacement(0));
            assertThrows(IllegalArgumentException.class, () -> picker.pickWithoutReplacement(-1));
            assertThrows(IllegalArgumentException.class, () -> picker.pickWithoutReplacement(3));
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Thread-safe pick execution under concurrent threads")
        void testConcurrentPicking() throws InterruptedException {
            WeightedRandomPicker<Integer> picker = WeightedRandomPicker.<Integer>builder()
                    .add(1, 10.0)
                    .add(2, 20.0)
                    .add(3, 30.0)
                    .build();

            int threads = 10;
            int picksPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < picksPerThread; i++) {
                            Integer val = picker.pick();
                            assertNotNull(val);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();
        }
    }
}
