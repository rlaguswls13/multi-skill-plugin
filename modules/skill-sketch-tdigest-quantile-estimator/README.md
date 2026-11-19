# `skill-sketch-tdigest-quantile-estimator`

Zero-dependency Java 21 **Merging T-Digest** streaming quantile estimator implementing Ted Dunning's **$K_1$ (arcsin) scale function** for bounded-memory, high-precision extreme tail quantiles (`p95`, `p99`, `p99.9`).

## Highlights
- **$K_1$ Arcsin Scale Function**: Concentrates centroid resolution near $q = 0$ and $q = 1$, achieving sub-percent relative error at `p99` and `p99.9`.
- **Associative Merging**: Combine digests from distributed workers/shards via `merge(TDigest)` with alternating merge direction to reduce drift.
- **Weighted Ingestion & CDF**: Supports `add(value)`, `add(value, weight)`, `quantile(q)`, `p50()`/`p90()`/`p95()`/`p99()`/`p999()`, and `cdf(x)`.
- **Compact Binary Codec**: `toBytes()` / `fromBytes(byte[])` serializes header + centroids for network transport or storage.
- **Thread-Safe**: All mutating and query operations are synchronized.

## Quick Start

```java
import com.msp.tdigest.TDigest;

public class Example {
    public static void main(String[] args) {
        TDigest digest = TDigest.create(200.0);
        for (int i = 1; i <= 10_000; i++) {
            digest.add(i * 0.1);
        }

        System.out.printf("p50=%.2f, p95=%.2f, p99=%.2f, p99.9=%.2f%n",
                digest.p50(), digest.p95(), digest.p99(), digest.p999());
        System.out.printf("CDF(500.0)=%.4f, centroids=%d%n",
                digest.cdf(500.0), digest.centroidCount());

        // Serialize and restore
        byte[] bytes = digest.toBytes();
        TDigest restored = TDigest.fromBytes(bytes);
        System.out.println("Restored p99: " + restored.p99());
    }
}
```
