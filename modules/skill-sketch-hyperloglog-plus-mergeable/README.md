# skill-sketch-hyperloglog-plus-mergeable

Zero-dependency Java 21 **HyperLogLog++** cardinality estimator with sparse-to-dense transition, bias correction, lossless merging, and compact binary serialization.

## Architecture Highlights
- **64-bit Hashing**: Built-in `Hash64.murmur3` (x64 128-bit variant) and `Hash64.splitMix64`
- **Sparse-to-Dense Transition**: Primitive open-addressed `SparseRegisterMap` (zero boxing, 31-bit packed `(k, rho)` entries) automatically transitions to dense `byte[2^p]` when sparse size exceeds `m / 4`
- **Bias Correction**: High-precision `2^25` LinearCounting in sparse mode; HLL++ LinearCounting threshold + Ertl's unbiased harmonic estimator in dense mode
- **Lossless Merge**: Merge any combination of sparse and dense sketches of equal precision without loss
- **Compact Binary Codec**: `toBytes()` / `fromBytes(byte[])` with `HLL+` header and mode-aware encoding

## Usage Example
```java
import com.msp.hllplus.HyperLogLogPlus;
import java.util.List;

HyperLogLogPlus shardA = new HyperLogLogPlus(14);
HyperLogLogPlus shardB = new HyperLogLogPlus(14);

for (int i = 0; i < 10_000; i++) shardA.add("user-" + i);
for (int i = 5_000; i < 15_000; i++) shardB.add("user-" + i);

HyperLogLogPlus total = HyperLogLogPlus.mergeAll(List.of(shardA, shardB));
long distinctUsers = total.estimate(); // ~15,000

byte[] serialized = total.toBytes();
HyperLogLogPlus restored = HyperLogLogPlus.fromBytes(serialized);
```
