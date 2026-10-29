# 📊 skill-percentile-calculator

> **Streaming and batch percentile (P50, P90, P95, P99), IQR, and histogram calculator**  
> Pure Java 21 zero-dependency library providing thread-safe statistics, linear interpolation percentiles (NIST / R-7), frequency histograms, and infinite-stream reservoir sampling (Algorithm R).

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: 100% Java 21 standard library.
- **Percentile Calculation**:
  - Exact percentiles (`P50`, `P90`, `P95`, `P99`, or any custom `getPercentile(p)`).
  - Standard linear interpolation rank method (Type 7 / Excel `PERCENTILE.INC`).
  - Comprehensive summary metrics: `min`, `max`, `mean`, `median`, `variance`, `standardDeviation`, and Interquartile Range (`IQR = Q3 - Q1`).
- **Histogram Distribution**:
  - Uniform binning into $K$ intervals with count and percentage per bucket.
  - Ascii bar chart rendering for CLI / debug logs.
- **Reservoir Sampling (Algorithm R)**:
  - Maintains a fixed-size memory sample from unbounded or infinite streams with mathematical uniform probability.
- **Thread-Safe**: Safe for concurrent updates across virtual threads and background workers.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-percentile-calculator:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Latency Percentile & IQR Calculation

```java
import com.msp.stats.PercentileCalculator;

PercentileCalculator metrics = new PercentileCalculator();

// Record response latencies in milliseconds
metrics.addAll(12.5, 14.1, 15.0, 18.2, 22.0, 35.4, 120.0, 1500.2);

System.out.printf("Count: %d%n", metrics.getCount());
System.out.printf("Mean Latency: %.2f ms%n", metrics.getMean());
System.out.printf("P50 (Median): %.2f ms%n", metrics.getP50());
System.out.printf("P90 Latency:  %.2f ms%n", metrics.getP90());
System.out.printf("P99 Latency:  %.2f ms%n", metrics.getP99());
System.out.printf("IQR (Q3-Q1):  %.2f ms%n", metrics.getIQR());
```

### 2. Frequency Histogram & ASCII Chart

```java
import com.msp.stats.Histogram;
import com.msp.stats.Bucket;
import java.util.List;

List<Double> scores = List.of(55.0, 62.0, 78.0, 81.0, 85.0, 92.0, 95.0, 98.0);
Histogram histogram = Histogram.of(scores, 4);

for (Bucket bucket : histogram.getBuckets()) {
    System.out.printf("[%.1f - %.1f]: %d items (%.1f%%)%n",
            bucket.lowerBound(), bucket.upperBound(), bucket.count(), bucket.percentage());
}

// Print visual ASCII chart
System.out.println(histogram.renderAscii(20));
```

### 3. Infinite Stream Reservoir Sampling

```java
import com.msp.stats.ReservoirSampler;
import com.msp.stats.PercentileCalculator;

// Maintain an unbiased sample of 1,000 items from an unbounded stream
ReservoirSampler sampler = new ReservoirSampler(1_000);

// Ingest millions of incoming events
for (double eventVal : incomingDataStream) {
    sampler.add(eventVal);
}

// Export sample into PercentileCalculator for immediate quantile analysis
PercentileCalculator pCalc = sampler.toPercentileCalculator();
System.out.println("Stream P95: " + pCalc.getP95());
```
