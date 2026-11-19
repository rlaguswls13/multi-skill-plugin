# 🎲 starter-probabilistic-algo

> **Probabilistic Data Structures, Streaming Sketches, Roaring Bitmaps, and Consistent Hashing Starter Bundle**

Aggregates 6 zero-dependency Java 21 modules:
- `skill-bloom-filter-lite` — Thread-safe Bloom Filter (MurmurHash3 double-hashing, 0% false negatives)
- `skill-sketch-hyperloglog-plus-mergeable` — Mergeable HyperLogLog++ cardinality estimator (sparse & dense modes)
- `skill-sketch-tdigest-quantile-estimator` — Merging T-Digest for accurate streaming tail quantiles (p95, p99, p99.9)
- `skill-sketch-count-min-lite` — Count-Min Sketch & lock-free `AtomicCountMinSketch` with heavy-hitter detection
- `skill-bitmap-roaring-compressed-lite` — Compressed 32-bit Roaring Bitmap (Array, Bitmap, and RLE Run containers)
- `skill-consistent-hashing` — Virtual-node Consistent Hash Ring for distributed sharding & replication

## Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:starter-probabilistic-algo:2.0.0'
}
```

Or via JitPack:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.rlaguswls13.multi-skill-plugin:starter-probabilistic-algo:2.0.0'
}
```

## Quick Start

```java
import com.msp.starter.probabilistic.ProbabilisticAlgoKit;
import java.util.List;

// 1. Bloom Filter
var bloom = ProbabilisticAlgoKit.stringBloomFilter(100_000, 0.01);
bloom.put("user-42");
boolean seen = bloom.mightContain("user-42");

// 2. HyperLogLog++ (mergeable distinct count)
var hll = ProbabilisticAlgoKit.hyperLogLog();
hll.add("visitor-1");
long uniqueUsers = hll.estimate();

// 3. T-Digest (tail latency quantiles)
var latency = ProbabilisticAlgoKit.tDigest();
latency.add(12.5);
double p99 = latency.quantile(0.99);

// 4. Count-Min Sketch (frequency & heavy hitters)
var cms = ProbabilisticAlgoKit.countMinSketchWithHeavyHitters(0.01, 0.01, 0.1);
cms.update("/api/search", 500);
long estFreq = cms.estimateFrequency("/api/search");

// 5. Roaring Bitmap (compressed integer sets)
var setA = ProbabilisticAlgoKit.roaringBitmapOf(1, 2, 3);
var setB = ProbabilisticAlgoKit.roaringBitmapOf(2, 3, 4);
int[] common = setA.and(setB).toArray(); // [2, 3]

// 6. Consistent Hash Ring (sharding & replicas)
var ring = ProbabilisticAlgoKit.consistentHashRing(150, List.of("node-1", "node-2", "node-3"));
String shard = ring.getNodeOrThrow("session-abc");
```
