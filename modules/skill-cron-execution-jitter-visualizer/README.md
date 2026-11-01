# Cron Execution Jitter Visualizer (`skill-cron-execution-jitter-visualizer`)

Zero-dependency Java 21 library for simulating, statistically analyzing, and ASCII-visualizing the dispersion effect of jitter applied to large fleets of concurrent cron workers.

## Features

- **Jitter Strategies**:
  - `UNIFORM`: Flat random distribution over $[0, \text{maxJitter})$.
  - `FULL_JITTER`: AWS Full Jitter algorithm.
  - `EQUAL_JITTER`: AWS Equal Jitter ($\text{maxJitter}/2 + \text{random}(\text{maxJitter}/2)$) providing guaranteed base spacing.
  - `GAUSSIAN`: Normal distribution centered at $\text{maxJitter}/2$ with $3\sigma$ bounds.
- **Statistical Dispersion Analysis**:
  - Min, Max, Mean, Median, Variance, Standard Deviation ($\sigma$), and Coefficient of Variation ($CV = \sigma / \mu$).
  - Quantifies **Peak Concurrency Reduction** (e.g. from 1,000 simultaneous spikes down to $<100$ workers).
- **Terminal Visualizations**:
  - Horizontal ASCII histogram with configurable width and UTF-8 block characters (`████`).
  - Formatted metrics report table.
  - Multi-strategy comparison table.

## Runnable Code Example

```java
import com.msp.jittervis.JitterSimulationConfig;
import com.msp.jittervis.JitterSimulationResult;
import com.msp.jittervis.JitterSimulator;
import com.msp.jittervis.JitterStrategy;

public class JitterExample {
    public static void main(String[] args) {
        // 1. Configure simulation of 5,000 workers with 30s jitter window
        JitterSimulationConfig config = JitterSimulationConfig.builder()
                .workerCount(5000)
                .maxJitterMs(30_000L)
                .strategy(JitterStrategy.FULL_JITTER)
                .histogramBins(12)
                .build();

        // 2. Run simulation
        JitterSimulationResult result = JitterSimulator.simulate(config);

        // 3. Print ASCII Histogram
        System.out.println(result.renderHistogram(35));

        // 4. Print Detailed Report Table
        System.out.println(result.renderReportTable());

        // 5. Compare All Strategies
        System.out.println(JitterSimulator.compareStrategies(5000, 30_000L, 42L));
    }
}
```
