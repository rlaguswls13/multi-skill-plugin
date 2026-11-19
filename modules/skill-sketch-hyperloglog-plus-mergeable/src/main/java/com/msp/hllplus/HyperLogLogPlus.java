package com.msp.hllplus;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Objects;

/**
 * Thread-safe HyperLogLog++ cardinality estimator with:
 * <ul>
 *   <li>64-bit MurmurHash3 / SplitMix64 hashing</li>
 *   <li>Automatic sparse-to-dense register transition</li>
 *   <li>LinearCounting in sparse mode and bias-corrected Ertl/HLL++ estimation in dense mode</li>
 *   <li>Lossless {@link #merge(HyperLogLogPlus)} across instances of equal precision</li>
 *   <li>Compact binary serialization and deserialization</li>
 * </ul>
 */
public final class HyperLogLogPlus {

    public static final int MIN_PRECISION = 4;
    public static final int MAX_PRECISION = 18;
    public static final int DEFAULT_PRECISION = 14;
    public static final int MAX_SPARSE_PRECISION = 25;
    public static final int DEFAULT_SPARSE_PRECISION = 25;

    private static final int MAGIC = 0x484C4C2B; // "HLL+"
    private static final byte VERSION = 1;
    private static final byte MODE_SPARSE = 0;
    private static final byte MODE_DENSE = 1;

    private static final double ALPHA_INF = 1.0 / (2.0 * Math.log(2.0));

    /** Standard HLL++ LinearCounting thresholds for precision p = 4..18. */
    private static final double[] LC_THRESHOLDS = {
            10, 20, 40, 80, 220, 400, 900, 1800, 3100, 6500, 11500, 20000, 50000, 120000, 350000
    };

    private final int p;
    private final int pPrime;
    private final int m;
    private final int sparseThreshold;

    private boolean sparse;
    private SparseRegisterMap sparseMap;
    private byte[] denseRegisters;

    /**
     * Creates a HyperLogLog++ sketch with default precision {@code p = 14} (m = 16384)
     * and sparse precision {@code p' = 25}.
     */
    public HyperLogLogPlus() {
        this(DEFAULT_PRECISION, DEFAULT_SPARSE_PRECISION);
    }

    /**
     * Creates a HyperLogLog++ sketch with precision {@code p} and default sparse precision {@code 25}.
     */
    public HyperLogLogPlus(int precision) {
        this(precision, Math.max(precision, DEFAULT_SPARSE_PRECISION));
    }

    /**
     * Creates a HyperLogLog++ sketch with precision {@code p} ({@code 4..18})
     * and sparse precision {@code pPrime} ({@code p..25}).
     */
    public HyperLogLogPlus(int precision, int sparsePrecision) {
        if (precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(
                    "precision must be in [" + MIN_PRECISION + ", " + MAX_PRECISION + "], got " + precision);
        }
        if (sparsePrecision < precision || sparsePrecision > MAX_SPARSE_PRECISION) {
            throw new IllegalArgumentException(
                    "sparsePrecision must be in [" + precision + ", " + MAX_SPARSE_PRECISION + "], got " + sparsePrecision);
        }
        this.p = precision;
        this.pPrime = sparsePrecision;
        this.m = 1 << precision;
        // Transition to dense when sparse map reaches m / 4 entries (same memory footprint as byte[m])
        this.sparseThreshold = Math.max(8, this.m / 4);
        this.sparse = true;
        this.sparseMap = new SparseRegisterMap(Math.min(64, this.sparseThreshold));
    }

    private HyperLogLogPlus(int p, int pPrime, boolean sparse, SparseRegisterMap sparseMap, byte[] denseRegisters) {
        this.p = p;
        this.pPrime = pPrime;
        this.m = 1 << p;
        this.sparseThreshold = Math.max(8, this.m / 4);
        this.sparse = sparse;
        this.sparseMap = sparseMap;
        this.denseRegisters = denseRegisters;
    }

    public int precision() {
        return p;
    }

    public int sparsePrecision() {
        return pPrime;
    }

    public int registerCount() {
        return m;
    }

    public synchronized boolean isSparse() {
        return sparse;
    }

    public void add(String value) {
        Objects.requireNonNull(value, "value must not be null");
        addHash(Hash64.murmur3(value));
    }

    public void add(byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        addHash(Hash64.murmur3(value));
    }

    public void add(long value) {
        addHash(Hash64.splitMix64(value));
    }

    /**
     * Adds a pre-computed 64-bit uniform hash directly into the sketch.
     */
    public synchronized void addHash(long hash64) {
        int q = 64 - p;
        long w = hash64 << p;
        byte rho = (byte) (w == 0L ? q + 1 : Long.numberOfLeadingZeros(w) + 1);

        if (sparse) {
            int sparseIndex = (int) (hash64 >>> (64 - pPrime));
            sparseMap.putMax(sparseIndex, rho);
            if (sparseMap.size() > sparseThreshold) {
                convertToDense();
            }
        } else {
            int idx = (int) (hash64 >>> (64 - p));
            if (rho > denseRegisters[idx]) {
                denseRegisters[idx] = rho;
            }
        }
    }

    /**
     * Estimates the number of distinct elements added to this sketch.
     */
    public synchronized long estimate() {
        if (sparse) {
            int count = sparseMap.size();
            if (count == 0) {
                return 0L;
            }
            double mPrime = (double) (1L << pPrime);
            double est = mPrime * Math.log(mPrime / (mPrime - count));
            return Math.round(est);
        }
        return estimateDense();
    }

    /**
     * Losslessly merges {@code other} into this sketch. Both sketches must share the same precision {@code p}.
     */
    public void merge(HyperLogLogPlus other) {
        Objects.requireNonNull(other, "other must not be null");
        if (other == this) {
            return;
        }
        if (this.p != other.p) {
            throw new IllegalArgumentException(
                    "Cannot merge sketches with different precisions: " + this.p + " vs " + other.p);
        }

        // Snapshot other under its monitor to avoid deadlocks when merging concurrently
        boolean otherSparse;
        int otherPPrime;
        int[] otherSparsePacked = null;
        byte[] otherDense = null;
        synchronized (other) {
            otherSparse = other.sparse;
            otherPPrime = other.pPrime;
            if (otherSparse) {
                otherSparsePacked = other.sparseMap.toSortedPackedArray();
            } else {
                otherDense = other.denseRegisters.clone();
            }
        }

        synchronized (this) {
            if (this.sparse && otherSparse && this.pPrime == otherPPrime) {
                for (int packed : otherSparsePacked) {
                    int k = packed >>> 6;
                    byte rho = (byte) (packed & 0x3F);
                    this.sparseMap.putMax(k, rho);
                }
                if (this.sparseMap.size() > this.sparseThreshold) {
                    convertToDense();
                }
                return;
            }

            if (this.sparse) {
                convertToDense();
            }

            if (otherSparse) {
                int shift = otherPPrime - this.p;
                for (int packed : otherSparsePacked) {
                    int k = packed >>> 6;
                    byte rho = (byte) (packed & 0x3F);
                    int idx = k >>> shift;
                    if (rho > this.denseRegisters[idx]) {
                        this.denseRegisters[idx] = rho;
                    }
                }
            } else {
                for (int i = 0; i < this.m; i++) {
                    if (otherDense[i] > this.denseRegisters[i]) {
                        this.denseRegisters[i] = otherDense[i];
                    }
                }
            }
        }
    }

    /**
     * Merges a non-empty collection of sketches of equal precision into a new {@link HyperLogLogPlus}.
     */
    public static HyperLogLogPlus mergeAll(Collection<HyperLogLogPlus> sketches) {
        Objects.requireNonNull(sketches, "sketches must not be null");
        if (sketches.isEmpty()) {
            throw new IllegalArgumentException("sketches must not be empty");
        }
        HyperLogLogPlus result = null;
        for (HyperLogLogPlus s : sketches) {
            Objects.requireNonNull(s, "sketch element must not be null");
            if (result == null) {
                result = s.copy();
            } else {
                result.merge(s);
            }
        }
        return result;
    }

    /**
     * Returns an independent deep copy of this sketch.
     */
    public synchronized HyperLogLogPlus copy() {
        if (sparse) {
            return new HyperLogLogPlus(p, pPrime, true, sparseMap.copy(), null);
        }
        return new HyperLogLogPlus(p, pPrime, false, null, denseRegisters.clone());
    }

    /**
     * Serializes this sketch into a compact binary format.
     *
     * <p>Format: 4-byte magic ({@code HLL+}), 1-byte version, 1-byte {@code p}, 1-byte {@code pPrime},
     * 1-byte mode ({@code 0=SPARSE, 1=DENSE}), followed by either:
     * <ul>
     *   <li>SPARSE: 4-byte count + sorted 4-byte packed {@code (k << 6) | rho} entries</li>
     *   <li>DENSE: {@code 2^p} register bytes</li>
     * </ul></p>
     */
    public synchronized byte[] toBytes() {
        if (sparse) {
            int[] packed = sparseMap.toSortedPackedArray();
            ByteBuffer buf = ByteBuffer.allocate(8 + 4 + packed.length * 4);
            buf.putInt(MAGIC);
            buf.put(VERSION);
            buf.put((byte) p);
            buf.put((byte) pPrime);
            buf.put(MODE_SPARSE);
            buf.putInt(packed.length);
            for (int entry : packed) {
                buf.putInt(entry);
            }
            return buf.array();
        } else {
            ByteBuffer buf = ByteBuffer.allocate(8 + m);
            buf.putInt(MAGIC);
            buf.put(VERSION);
            buf.put((byte) p);
            buf.put((byte) pPrime);
            buf.put(MODE_DENSE);
            buf.put(denseRegisters);
            return buf.array();
        }
    }

    /**
     * Deserializes a sketch previously created by {@link #toBytes()}.
     */
    public static HyperLogLogPlus fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length < 8) {
            throw new IllegalArgumentException("Truncated HLL+ header: length=" + bytes.length);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        int magic = buf.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Invalid HLL+ magic header: 0x" + Integer.toHexString(magic));
        }
        byte version = buf.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported HLL+ version: " + version);
        }
        int p = buf.get() & 0xFF;
        int pPrime = buf.get() & 0xFF;
        byte mode = buf.get();

        if (p < MIN_PRECISION || p > MAX_PRECISION) {
            throw new IllegalArgumentException("Invalid precision in stream: " + p);
        }
        if (pPrime < p || pPrime > MAX_SPARSE_PRECISION) {
            throw new IllegalArgumentException("Invalid sparsePrecision in stream: " + pPrime);
        }

        if (mode == MODE_SPARSE) {
            if (buf.remaining() < 4) {
                throw new IllegalArgumentException("Truncated sparse count");
            }
            int count = buf.getInt();
            if (count < 0 || buf.remaining() != count * 4) {
                throw new IllegalArgumentException("Invalid sparse payload length for count=" + count);
            }
            SparseRegisterMap map = new SparseRegisterMap(Math.max(16, count * 2));
            for (int i = 0; i < count; i++) {
                int packed = buf.getInt();
                int k = packed >>> 6;
                byte rho = (byte) (packed & 0x3F);
                map.putMax(k, rho);
            }
            return new HyperLogLogPlus(p, pPrime, true, map, null);
        } else if (mode == MODE_DENSE) {
            int m = 1 << p;
            if (buf.remaining() != m) {
                throw new IllegalArgumentException("Invalid dense payload length: expected " + m + ", got " + buf.remaining());
            }
            byte[] regs = new byte[m];
            buf.get(regs);
            return new HyperLogLogPlus(p, pPrime, false, null, regs);
        } else {
            throw new IllegalArgumentException("Unknown HLL+ mode byte: " + mode);
        }
    }

    private void convertToDense() {
        byte[] regs = new byte[m];
        int shift = pPrime - p;
        sparseMap.forEach((k, rho) -> {
            int idx = k >>> shift;
            if (rho > regs[idx]) {
                regs[idx] = rho;
            }
        });
        this.denseRegisters = regs;
        this.sparseMap = null;
        this.sparse = false;
    }

    /**
     * Dense cardinality estimation using LinearCounting below the HLL++ threshold
     * and Ertl's improved bias-corrected estimator above the threshold.
     */
    private long estimateDense() {
        int q = 64 - p;
        int[] c = new int[q + 2];
        for (int i = 0; i < m; i++) {
            int v = denseRegisters[i] & 0xFF;
            if (v <= q + 1) {
                c[v]++;
            }
        }

        int zeros = c[0];
        if (zeros > 0) {
            double linear = m * Math.log((double) m / zeros);
            if (linear <= LC_THRESHOLDS[p - MIN_PRECISION]) {
                return Math.round(linear);
            }
        }

        // Ertl's improved HyperLogLog estimator (unbiased across all cardinalities)
        double z = m * tau(1.0 - (double) c[q + 1] / m);
        for (int k = q; k >= 1; k--) {
            z += c[k];
            z *= 0.5;
        }
        z += m * sigma((double) zeros / m);
        double estimate = ALPHA_INF * m * m / z;
        return Math.round(estimate);
    }

    private static double sigma(double x) {
        if (x == 1.0) {
            return Double.POSITIVE_INFINITY;
        }
        double zPrime;
        double y = 1.0;
        double z = x;
        do {
            x *= x;
            zPrime = z;
            z += x * y;
            y += y;
        } while (zPrime != z);
        return z;
    }

    private static double tau(double x) {
        if (x == 0.0 || x == 1.0) {
            return 0.0;
        }
        double zPrime;
        double y = 1.0;
        double z = 1.0 - x;
        do {
            x = Math.sqrt(x);
            zPrime = z;
            y *= 0.5;
            double diff = 1.0 - x;
            z -= diff * diff * y;
        } while (zPrime != z);
        return z / 3.0;
    }
}
