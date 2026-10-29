package com.msp.hll;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * HyperLogLog (HLL) cardinality estimator with 64-bit hashing and small cardinality bias correction.
 * <p>
 * Provides memory-efficient cardinality estimation with precision $b \in [4, 16]$ registers ($m = 2^b$),
 * achieving theoretical standard error bounded by $1.04 / \sqrt{m}$.
 * Utilizes LinearCounting for small cardinalities ($E \le 2.5m$) to maintain high accuracy.
 */
public class HyperLogLog {

    public static final int DEFAULT_PRECISION = 12; // 4096 registers, ~1.625% standard error, 4KB footprint
    public static final int MIN_PRECISION = 4;
    public static final int MAX_PRECISION = 16;

    private final int precision;
    private final int m; // 2^precision registers
    private final double alphaM;
    private final byte[] registers;

    /**
     * Constructs a HyperLogLog with default precision (b = 12, m = 4096).
     */
    public HyperLogLog() {
        this(DEFAULT_PRECISION);
    }

    /**
     * Constructs a HyperLogLog with a specified precision $b \in [4, 16]$.
     *
     * @param precision register bit precision (4 to 16)
     */
    public HyperLogLog(int precision) {
        if (precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(
                    "Precision must be between " + MIN_PRECISION + " and " + MAX_PRECISION + ", got: " + precision);
        }
        this.precision = precision;
        this.m = 1 << precision;
        this.alphaM = computeAlpha(m);
        this.registers = new byte[m];
    }

    /**
     * Copy constructor / deserialization constructor.
     *
     * @param precision register bit precision
     * @param registers register state
     */
    public HyperLogLog(int precision, byte[] registers) {
        if (precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(
                    "Precision must be between " + MIN_PRECISION + " and " + MAX_PRECISION + ", got: " + precision);
        }
        int expectedLength = 1 << precision;
        if (registers.length != expectedLength) {
            throw new IllegalArgumentException(
                    "Registers length must be " + expectedLength + " for precision " + precision + ", got: " + registers.length);
        }
        this.precision = precision;
        this.m = expectedLength;
        this.alphaM = computeAlpha(m);
        this.registers = Arrays.copyOf(registers, registers.length);
    }

    /**
     * Adds a string element to the estimator.
     *
     * @param value string value
     */
    public void add(String value) {
        Objects.requireNonNull(value, "value cannot be null");
        add(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Adds a 64-bit integer element to the estimator.
     *
     * @param value 64-bit integer
     */
    public void add(long value) {
        byte[] bytes = new byte[8];
        bytes[0] = (byte) value;
        bytes[1] = (byte) (value >>> 8);
        bytes[2] = (byte) (value >>> 16);
        bytes[3] = (byte) (value >>> 24);
        bytes[4] = (byte) (value >>> 32);
        bytes[5] = (byte) (value >>> 40);
        bytes[6] = (byte) (value >>> 48);
        bytes[7] = (byte) (value >>> 56);
        add(bytes);
    }

    /**
     * Adds arbitrary byte array data to the estimator.
     *
     * @param data byte array
     */
    public void add(byte[] data) {
        Objects.requireNonNull(data, "data cannot be null");
        long hash = murmur3_64(data);

        // First precision bits index the register
        int j = (int) (hash >>> (64 - precision));

        // Remaining 64 - precision bits define the run of leading zeros
        long w = hash << precision;
        int leadingZeros = Long.numberOfLeadingZeros(w);
        int rank = Math.min(leadingZeros + 1, 64 - precision + 1);

        if (rank > registers[j]) {
            registers[j] = (byte) rank;
        }
    }

    /**
     * Estimates the distinct cardinality of elements added so far.
     *
     * @return estimated distinct count
     */
    public long cardinality() {
        double sum = 0.0;
        int zeroCount = 0;

        for (int i = 0; i < m; i++) {
            byte reg = registers[i];
            sum += Math.scalb(1.0, -reg);
            if (reg == 0) {
                zeroCount++;
            }
        }

        double rawEstimate = alphaM * m * m / sum;

        // Small cardinality bias correction using LinearCounting
        if (rawEstimate <= 2.5 * m) {
            if (zeroCount > 0) {
                return Math.round(m * Math.log((double) m / zeroCount));
            }
        }

        return Math.round(rawEstimate);
    }

    /**
     * Merges another HyperLogLog estimator of identical precision into this instance in-place.
     *
     * @param other another HyperLogLog estimator
     */
    public void merge(HyperLogLog other) {
        Objects.requireNonNull(other, "other cannot be null");
        if (this.precision != other.precision) {
            throw new IllegalArgumentException(
                    "Cannot merge HLL instances with different precisions: " + this.precision + " vs " + other.precision);
        }

        for (int i = 0; i < m; i++) {
            if (other.registers[i] > this.registers[i]) {
                this.registers[i] = other.registers[i];
            }
        }
    }

    /**
     * Creates a new HyperLogLog containing the union of two estimators.
     *
     * @param first  first estimator
     * @param second second estimator
     * @return union estimator
     */
    public static HyperLogLog merge(HyperLogLog first, HyperLogLog second) {
        Objects.requireNonNull(first, "first cannot be null");
        Objects.requireNonNull(second, "second cannot be null");
        HyperLogLog copy = new HyperLogLog(first.precision, first.registers);
        copy.merge(second);
        return copy;
    }

    /**
     * Returns the precision $b$.
     */
    public int getPrecision() {
        return precision;
    }

    /**
     * Returns the number of registers $m = 2^b$.
     */
    public int getRegisterCount() {
        return m;
    }

    /**
     * Returns theoretical standard error: $1.04 / \sqrt{m}$.
     */
    public double getExpectedError() {
        return 1.04 / Math.sqrt(m);
    }

    /**
     * Returns a defensive copy of the register array.
     */
    public byte[] getRegisters() {
        return Arrays.copyOf(registers, registers.length);
    }

    /**
     * Resets all registers to zero.
     */
    public void reset() {
        Arrays.fill(registers, (byte) 0);
    }

    private static double computeAlpha(int m) {
        if (m == 16) return 0.673;
        if (m == 32) return 0.697;
        if (m == 64) return 0.709;
        return 0.7213 / (1.0 + 1.079 / m);
    }

    private static long murmur3_64(byte[] data) {
        long h = 0x9747b28cL;
        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;
        int len = data.length;
        int nblocks = len / 8;

        for (int i = 0; i < nblocks; i++) {
            int idx = i * 8;
            long k = (((long) data[idx] & 0xFF))
                    | (((long) data[idx + 1] & 0xFF) << 8)
                    | (((long) data[idx + 2] & 0xFF) << 16)
                    | (((long) data[idx + 3] & 0xFF) << 24)
                    | (((long) data[idx + 4] & 0xFF) << 32)
                    | (((long) data[idx + 5] & 0xFF) << 40)
                    | (((long) data[idx + 6] & 0xFF) << 48)
                    | (((long) data[idx + 7] & 0xFF) << 56);

            k *= c1;
            k = Long.rotateLeft(k, 31);
            k *= c2;
            h ^= k;
            h = Long.rotateLeft(h, 27);
            h = h * 5 + 0x52dce729L;
        }

        long k1 = 0;
        int tailStart = nblocks * 8;
        switch (len & 7) {
            case 7:
                k1 ^= ((long) data[tailStart + 6] & 0xFF) << 48;
            case 6:
                k1 ^= ((long) data[tailStart + 5] & 0xFF) << 40;
            case 5:
                k1 ^= ((long) data[tailStart + 4] & 0xFF) << 32;
            case 4:
                k1 ^= ((long) data[tailStart + 3] & 0xFF) << 24;
            case 3:
                k1 ^= ((long) data[tailStart + 2] & 0xFF) << 16;
            case 2:
                k1 ^= ((long) data[tailStart + 1] & 0xFF) << 8;
            case 1:
                k1 ^= ((long) data[tailStart] & 0xFF);
                k1 *= c1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= c2;
                h ^= k1;
                break;
            default:
                break;
        }

        h ^= len;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }
}
