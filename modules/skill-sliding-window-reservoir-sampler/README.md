# Sliding Window Reservoir Sampler (`skill-sliding-window-reservoir-sampler`)

Zero-dependency, thread-safe implementation of Alan G. Waterman's **Reservoir Sampling Algorithm R** in pure Java 21. Extracts an unbiased uniform random representative sample of size `k` from unbounded, continuous data streams where the total item count `n` is unknown or infinite.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Uniform Mathematical Distribution**: Every item has an exact `k/n` probability of being chosen in the final sample pool.
- **Constant Memory Footprint**: Keeps at most `O(k)` items in memory, regardless of how many millions of items are processed.
- **Thread-Safe**: Fully synchronized operations for concurrent ingest pipelines.
- **Deterministic Testing Support**: Accepts custom `Random` instance for reproducible sampling tests.

---

## 📦 Usage Examples

### 1. Basic Streaming Sampling

```java
import com.msp.reservoir.ReservoirSampler;
import java.util.List;

// Retain a representative sample of 10 items
ReservoirSampler<String> sampler = new ReservoirSampler<>(10);

// Ingest stream of events/logs/user IDs
for (int i = 1; i <= 100_000; i++) {
    sampler.sample("Event-" + i);
}

// Get the current snapshot of 10 uniform samples
List<String> representativeSamples = sampler.getSamples();
System.out.println("Total seen: " + sampler.totalSeenCount()); // 100000
System.out.println("Samples (10 items): " + representativeSamples);
```

### 2. Reset and Reuse

```java
sampler.clear();
System.out.println(sampler.totalSeenCount()); // 0
System.out.println(sampler.getSamples().size()); // 0
```
