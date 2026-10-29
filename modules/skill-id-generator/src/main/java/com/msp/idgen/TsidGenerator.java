package com.msp.idgen;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Thread-safe 64-bit Time-Sorted ID (TSID) generator with Crockford Base32 formatting.
 *
 * <p>Bit distribution:
 * <ul>
 *   <li>42 bits: Millisecond timestamp delta from epoch (default: 2020-01-01 UTC)</li>
 *   <li>10 bits: Node ID (0 - 1023)</li>
 *   <li>12 bits: Per-millisecond sequence counter (0 - 4095)</li>
 * </ul>
 *
 * <p>Formats to a compact, URL-safe, time-sortable 13-character Crockford Base32 string
 * or a raw 64-bit positive {@code long}.
 */
public final class TsidGenerator {

    /** Default epoch: 2020-01-01 00:00:00 UTC (1,577,836,800,000 ms). */
    public static final long DEFAULT_EPOCH = 1577836800000L;

    public static final long NODE_ID_BITS = 10L;
    public static final long SEQUENCE_BITS = 12L;
    public static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1L; // 1023
    public static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1L; // 4095

    private static final long TIMESTAMP_SHIFT = 22L;
    private static final long NODE_ID_SHIFT = 12L;

    /** Crockford's Base32 alphabet (32 characters, excluding I, L, O, U). */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final byte[] DECODE_TABLE = new byte[128];

    static {
        Arrays.fill(DECODE_TABLE, (byte) -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            DECODE_TABLE[ALPHABET[i]] = (byte) i;
            if (Character.isLetter(ALPHABET[i])) {
                DECODE_TABLE[Character.toLowerCase(ALPHABET[i])] = (byte) i;
            }
        }
        // Crockford lenient aliases: O/o -> 0, I/i/L/l -> 1
        DECODE_TABLE['O'] = 0;
        DECODE_TABLE['o'] = 0;
        DECODE_TABLE['I'] = 1;
        DECODE_TABLE['i'] = 1;
        DECODE_TABLE['L'] = 1;
        DECODE_TABLE['l'] = 1;
    }

    private final long nodeId;
    private final long epoch;
    private final LongSupplier timeSource;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Creates a TsidGenerator with a randomized node ID (0-1023) and default epoch.
     */
    public TsidGenerator() {
        this(new SecureRandom().nextInt(1024), DEFAULT_EPOCH, System::currentTimeMillis);
    }

    /**
     * Creates a TsidGenerator with a specified node ID (0-1023) and default epoch.
     *
     * @param nodeId node ID (0 - 1023)
     */
    public TsidGenerator(int nodeId) {
        this(nodeId, DEFAULT_EPOCH, System::currentTimeMillis);
    }

    /**
     * Full constructor.
     */
    public TsidGenerator(int nodeId, long epoch, LongSupplier timeSource) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID + ": " + nodeId);
        }
        if (epoch < 0) {
            throw new IllegalArgumentException("epoch cannot be negative: " + epoch);
        }
        this.nodeId = nodeId;
        this.epoch = epoch;
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource must not be null");
    }

    /**
     * Generates the next TSID as a 64-bit positive long.
     */
    public synchronized long nextLong() {
        long currentTimestamp = timeSource.getAsLong();

        if (currentTimestamp < lastTimestamp) {
            currentTimestamp = waitUntilNextTime(lastTimestamp);
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitUntilNextTime(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - epoch) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    /**
     * Generates the next TSID formatted as a 13-character Crockford Base32 string.
     */
    public String nextTsid() {
        return toBase32(nextLong());
    }

    private long waitUntilNextTime(long lastTs) {
        long current = timeSource.getAsLong();
        while (current <= lastTs) {
            Thread.onSpinWait();
            current = timeSource.getAsLong();
        }
        return current;
    }

    /**
     * Encodes a 64-bit TSID long into a 13-character Crockford Base32 string.
     */
    public static String toBase32(long id) {
        char[] chars = new char[13];
        // 1st char: bits 60-63 (4 bits)
        chars[0] = ALPHABET[(int) ((id >>> 60) & 0x0F)];
        // chars 1 to 12: 5 bits each
        for (int i = 1; i < 13; i++) {
            int shift = 60 - (5 * i);
            chars[i] = ALPHABET[(int) ((id >>> shift) & 0x1F)];
        }
        return new String(chars);
    }

    /**
     * Decodes a 13-character Crockford Base32 string back to a 64-bit TSID long.
     */
    public static long fromBase32(String tsid) {
        Objects.requireNonNull(tsid, "tsid string must not be null");
        String trimmed = tsid.trim();
        if (trimmed.length() != 13) {
            throw new IllegalArgumentException("TSID string must be exactly 13 characters: " + tsid);
        }

        long id = 0L;
        // First char (4 bits)
        char firstChar = trimmed.charAt(0);
        int firstVal = decodeChar(firstChar);
        if ((firstVal & ~0x0F) != 0) {
            throw new IllegalArgumentException("First character overflow in TSID: " + firstChar);
        }
        id |= ((long) firstVal) << 60;

        // Next 12 chars (5 bits each)
        for (int i = 1; i < 13; i++) {
            char c = trimmed.charAt(i);
            int val = decodeChar(c);
            int shift = 60 - (5 * i);
            id |= ((long) val) << shift;
        }

        return id;
    }

    private static int decodeChar(char c) {
        if (c >= 128) {
            throw new IllegalArgumentException("Invalid Base32 character: " + c);
        }
        byte val = DECODE_TABLE[c];
        if (val == -1) {
            throw new IllegalArgumentException("Invalid Base32 character: " + c);
        }
        return val;
    }

    /**
     * Extracts the millisecond timestamp from a 64-bit TSID long.
     */
    public static long extractTimestamp(long id, long epoch) {
        return (id >>> TIMESTAMP_SHIFT) + epoch;
    }

    /**
     * Extracts the Instant from a 64-bit TSID long.
     */
    public static Instant extractInstant(long id, long epoch) {
        return Instant.ofEpochMilli(extractTimestamp(id, epoch));
    }

    public long getNodeId() {
        return nodeId;
    }

    public long getEpoch() {
        return epoch;
    }
}
