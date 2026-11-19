package com.msp.starter.probabilistic;

import com.msp.bloom.BloomFilter;
import com.msp.bloom.Funnel;
import com.msp.bloom.Funnels;
import com.msp.countmin.AtomicCountMinSketch;
import com.msp.countmin.CountMinSketch;
import com.msp.hashring.ConsistentHashRing;
import com.msp.hllplus.HyperLogLogPlus;
import com.msp.roaring.RoaringBitmapLite;
import com.msp.tdigest.TDigest;

import java.util.Collection;
import java.util.Objects;

/**
 * Probabilistic Data Structures, Streaming Sketches, Compressed Bitmaps, and Consistent Hashing Starter Facade.
 *
 * <p>Unifies six zero-dependency Java 21 modules behind a single entry point:
 * <ul>
 *   <li>{@link BloomFilter} — memory-efficient set membership with 0% false negatives</li>
 *   <li>{@link HyperLogLogPlus} — mergeable distinct cardinality estimation (sparse &amp; dense)</li>
 *   <li>{@link TDigest} — accurate streaming tail quantile estimation (p50, p95, p99, p99.9)</li>
 *   <li>{@link CountMinSketch} / {@link AtomicCountMinSketch} — sub-linear frequency &amp; heavy-hitter tracking</li>
 *   <li>{@link RoaringBitmapLite} — compressed 32-bit integer sets with fast bitwise operations</li>
 *   <li>{@link ConsistentHashRing} — virtual-node consistent hashing for distributed sharding</li>
 * </ul>
 */
public final class ProbabilisticAlgoKit {

    private ProbabilisticAlgoKit() {
    }

    // -------------------------------------------------------------------------
    // 1. Bloom Filter
    // -------------------------------------------------------------------------

    /**
     * Creates a thread-safe Bloom filter for UTF-8 {@link String} elements.
     */
    public static BloomFilter<String> stringBloomFilter(long expectedInsertions, double falsePositiveRate) {
        return BloomFilter.create(Funnels.stringFunnel(), expectedInsertions, falsePositiveRate);
    }

    /**
     * Creates a thread-safe Bloom filter for {@link Long} elements.
     */
    public static BloomFilter<Long> longBloomFilter(long expectedInsertions, double falsePositiveRate) {
        return BloomFilter.create(Funnels.longFunnel(), expectedInsertions, falsePositiveRate);
    }

    /**
     * Creates a thread-safe Bloom filter for an arbitrary element type using a custom {@link Funnel}.
     */
    public static <T> BloomFilter<T> bloomFilter(Funnel<T> funnel, long expectedInsertions, double falsePositiveRate) {
        return BloomFilter.create(funnel, expectedInsertions, falsePositiveRate);
    }

    // -------------------------------------------------------------------------
    // 2. HyperLogLog++ Cardinality Estimator
    // -------------------------------------------------------------------------

    /**
     * Creates a mergeable {@link HyperLogLogPlus} sketch with default precision ({@code p = 14}).
     */
    public static HyperLogLogPlus hyperLogLog() {
        return new HyperLogLogPlus();
    }

    /**
     * Creates a mergeable {@link HyperLogLogPlus} sketch with explicit precision ({@code 4..18}).
     */
    public static HyperLogLogPlus hyperLogLog(int precision) {
        return new HyperLogLogPlus(precision);
    }

    // -------------------------------------------------------------------------
    // 3. T-Digest Quantile Estimator
    // -------------------------------------------------------------------------

    /**
     * Creates a streaming {@link TDigest} with default compression ({@code 100.0}).
     */
    public static TDigest tDigest() {
        return TDigest.create();
    }

    /**
     * Creates a streaming {@link TDigest} with custom compression factor ({@code >= 10.0}).
     */
    public static TDigest tDigest(double compression) {
        return TDigest.create(compression);
    }

    // -------------------------------------------------------------------------
    // 4. Count-Min Sketch
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link CountMinSketch} with {@code (epsilon, delta)} error bounds.
     */
    public static <T> CountMinSketch<T> countMinSketch(double epsilon, double delta) {
        return CountMinSketch.create(epsilon, delta);
    }

    /**
     * Creates a {@link CountMinSketch} with heavy-hitter tracking for items exceeding {@code phiThreshold} of total count.
     */
    public static <T> CountMinSketch<T> countMinSketchWithHeavyHitters(
            double epsilon, double delta, double phiThreshold) {
        return CountMinSketch.createWithHeavyHitters(epsilon, delta, phiThreshold);
    }

    /**
     * Creates a lock-free concurrent {@link AtomicCountMinSketch} with {@code (epsilon, delta)} error bounds.
     */
    public static <T> AtomicCountMinSketch<T> atomicCountMinSketch(double epsilon, double delta) {
        return AtomicCountMinSketch.create(epsilon, delta);
    }

    // -------------------------------------------------------------------------
    // 5. Roaring Bitmap
    // -------------------------------------------------------------------------

    /**
     * Creates an empty compressed {@link RoaringBitmapLite}.
     */
    public static RoaringBitmapLite roaringBitmap() {
        return new RoaringBitmapLite();
    }

    /**
     * Creates a compressed {@link RoaringBitmapLite} initialized with the given integers.
     */
    public static RoaringBitmapLite roaringBitmapOf(int... values) {
        return RoaringBitmapLite.bitmapOf(values);
    }

    // -------------------------------------------------------------------------
    // 6. Consistent Hash Ring
    // -------------------------------------------------------------------------

    /**
     * Creates a thread-safe {@link ConsistentHashRing} with default virtual nodes ({@code 150}).
     */
    public static <T> ConsistentHashRing<T> consistentHashRing() {
        return new ConsistentHashRing<>();
    }

    /**
     * Creates a thread-safe {@link ConsistentHashRing} with the specified number of virtual nodes per physical node.
     */
    public static <T> ConsistentHashRing<T> consistentHashRing(int virtualNodesPerNode) {
        return new ConsistentHashRing<>(virtualNodesPerNode);
    }

    /**
     * Creates a thread-safe {@link ConsistentHashRing} pre-populated with {@code initialNodes}.
     */
    public static <T> ConsistentHashRing<T> consistentHashRing(int virtualNodesPerNode, Collection<T> initialNodes) {
        Objects.requireNonNull(initialNodes, "initialNodes must not be null");
        ConsistentHashRing<T> ring = new ConsistentHashRing<>(virtualNodesPerNode);
        for (T node : initialNodes) {
            ring.addNode(node);
        }
        return ring;
    }
}
