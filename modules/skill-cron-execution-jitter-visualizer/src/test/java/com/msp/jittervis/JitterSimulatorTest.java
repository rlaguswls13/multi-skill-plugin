package com.msp.jittervis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Jitter Simulator & Visualizer Tests")
class JitterSimulatorTest {

    @Test
    @DisplayName("Simulates Uniform / Full Jitter with statistically sound dispersion")
    void testUniformJitter() {
        JitterSimulationConfig config = JitterSimulationConfig.builder()
                .workerCount(1000)
                .maxJitterMs(20000L)
                .strategy(JitterStrategy.FULL_JITTER)
                .histogramBins(10)
                .randomSeed(12345L)
                .build();

        JitterSimulationResult result = JitterSimulator.simulate(config);
        JitterStatistics stats = result.statistics();

        assertEquals(1000, result.delays().length);
        assertEquals(10, result.histogram().size());

        // Mean should be around 10,000ms (+- 1,000ms)
        assertTrue(stats.meanDelayMs() > 9000 && stats.meanDelayMs() < 11000,
                "Mean should be near half of max jitter: " + stats.meanDelayMs());

        // Min >= 0, Max <= 20000
        assertTrue(stats.minDelayMs() >= 0);
        assertTrue(stats.maxDelayMs() <= 20000L);

        // Concurrency reduction should be massive (> 80%)
        assertTrue(stats.collisionReductionPercent() > 80.0,
                "Collision reduction should exceed 80%, was: " + stats.collisionReductionPercent());
        assertTrue(stats.peakConcurrencyWithJitter() < 200);
    }

    @Test
    @DisplayName("Simulates Equal Jitter guaranteeing minimum base delay")
    void testEqualJitter() {
        long maxJitter = 10000L;
        JitterSimulationConfig config = JitterSimulationConfig.builder()
                .workerCount(500)
                .maxJitterMs(maxJitter)
                .strategy(JitterStrategy.EQUAL_JITTER)
                .randomSeed(42L)
                .build();

        JitterSimulationResult result = JitterSimulator.simulate(config);
        JitterStatistics stats = result.statistics();

        // Min delay must be at least maxJitter / 2 (5000ms)
        assertTrue(stats.minDelayMs() >= 5000L, "Equal jitter min delay must be >= 5000ms, was: " + stats.minDelayMs());
        // Mean should be around 7,500ms
        assertTrue(stats.meanDelayMs() > 7000 && stats.meanDelayMs() < 8000);
    }

    @Test
    @DisplayName("Simulates Gaussian Jitter centered at maxJitter / 2")
    void testGaussianJitter() {
        JitterSimulationConfig config = JitterSimulationConfig.builder()
                .workerCount(2000)
                .maxJitterMs(30000L)
                .strategy(JitterStrategy.GAUSSIAN)
                .histogramBins(10)
                .randomSeed(999L)
                .build();

        JitterSimulationResult result = JitterSimulator.simulate(config);
        JitterStatistics stats = result.statistics();

        // Clamped within [0, 30000]
        assertTrue(stats.minDelayMs() >= 0);
        assertTrue(stats.maxDelayMs() <= 30000L);

        // Center bins (e.g. bin 4 and 5) should have higher counts than outer bins (0 and 9)
        int centerCount = result.histogram().get(4).count() + result.histogram().get(5).count();
        int edgeCount = result.histogram().get(0).count() + result.histogram().get(9).count();
        assertTrue(centerCount > edgeCount, "Gaussian distribution center must have higher density than edges");
    }

    @Test
    @DisplayName("ASCII histogram and report table render correctly")
    void testAsciiRendering() {
        JitterSimulationConfig config = JitterSimulationConfig.builder()
                .workerCount(500)
                .maxJitterMs(10000L)
                .strategy(JitterStrategy.FULL_JITTER)
                .histogramBins(10)
                .randomSeed(42L)
                .build();

        JitterSimulationResult result = JitterSimulator.simulate(config);

        String histogram = result.renderHistogram(30);
        assertNotNull(histogram);
        assertTrue(histogram.contains("█"));
        assertTrue(histogram.contains("Workers=500"));

        String table = result.renderReportTable();
        assertNotNull(table);
        assertTrue(table.contains("Collision Reduction Rate"));
        assertTrue(table.contains("Standard Deviation"));
    }

    @Test
    @DisplayName("Comparative strategy summary generates across all 4 strategies")
    void testCompareStrategies() {
        String comparison = JitterSimulator.compareStrategies(1000, 20000L, 42L);
        assertNotNull(comparison);
        assertTrue(comparison.contains("UNIFORM"));
        assertTrue(comparison.contains("FULL_JITTER"));
        assertTrue(comparison.contains("EQUAL_JITTER"));
        assertTrue(comparison.contains("GAUSSIAN"));
    }

    @Test
    @DisplayName("Edge cases: 0 max jitter and single worker")
    void testEdgeCases() {
        JitterSimulationConfig zeroJitter = JitterSimulationConfig.builder()
                .workerCount(10)
                .maxJitterMs(0L)
                .build();
        JitterSimulationResult res = JitterSimulator.simulate(zeroJitter);
        assertEquals(0, res.statistics().minDelayMs());
        assertEquals(0, res.statistics().maxDelayMs());
        assertEquals(0.0, res.statistics().meanDelayMs());

        assertThrows(IllegalArgumentException.class, () -> JitterSimulationConfig.builder().workerCount(0).build());
        assertThrows(IllegalArgumentException.class, () -> JitterSimulationConfig.builder().maxJitterMs(-1L).build());
    }
}
