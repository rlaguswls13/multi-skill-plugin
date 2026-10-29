package com.msp.idgen;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Unified facade for ID generation utilities.
 *
 * <p>Supported ID types:
 * <ul>
 *   <li><b>Snowflake:</b> 64-bit distributed ID with 41-bit time, 10-bit node, 12-bit sequence.</li>
 *   <li><b>TSID:</b> 64-bit time-sorted ID, formatted as 13-char Crockford Base32.</li>
 *   <li><b>NanoID:</b> 21-char cryptographically secure URL-safe random string.</li>
 *   <li><b>UUIDv4:</b> Standard RFC 4122 random UUID.</li>
 *   <li><b>UUIDv7:</b> RFC 9562 time-ordered UUID with 48-bit timestamp.</li>
 * </ul>
 */
public final class IdGenerators {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final SnowflakeIdGenerator DEFAULT_SNOWFLAKE = new SnowflakeIdGenerator(1L);
    private static final TsidGenerator DEFAULT_TSID = new TsidGenerator(1);

    private IdGenerators() {
        // Utility class
    }

    /**
     * Returns the shared default Snowflake generator (nodeId = 1).
     */
    public static SnowflakeIdGenerator snowflake() {
        return DEFAULT_SNOWFLAKE;
    }

    /**
     * Creates a new Snowflake generator with a specific node ID (0 - 1023).
     */
    public static SnowflakeIdGenerator snowflake(long nodeId) {
        return new SnowflakeIdGenerator(nodeId);
    }

    /**
     * Returns the shared default TSID generator (nodeId = 1).
     */
    public static TsidGenerator tsid() {
        return DEFAULT_TSID;
    }

    /**
     * Creates a new TSID generator with a specific node ID (0 - 1023).
     */
    public static TsidGenerator tsid(int nodeId) {
        return new TsidGenerator(nodeId);
    }

    /**
     * Generates a default 21-character NanoID.
     */
    public static String nanoId() {
        return NanoIdGenerator.randomNanoId();
    }

    /**
     * Generates a NanoID with the specified length.
     */
    public static String nanoId(int size) {
        return NanoIdGenerator.randomNanoId(size);
    }

    /**
     * Generates a standard random UUIDv4 string.
     */
    public static String uuidV4() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a standard random UUIDv4 without dashes (32 hex characters).
     */
    public static String uuidV4NoDashes() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generates an RFC 9562 UUIDv7 (Unix millisecond timestamp + 74 bits of entropy).
     */
    public static UUID uuidV7() {
        long timestamp = System.currentTimeMillis();
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        // Most significant 64 bits: 48 bits timestamp | 4 bits version (7) | 12 bits random
        long msb = (timestamp & 0xFFFFFFFFFFFFL) << 16;
        msb |= (0x7L << 12);
        long randA = ((randomBytes[0] & 0xFFL) << 4) | ((randomBytes[1] & 0xF0L) >>> 4);
        msb |= (randA & 0x0FFFL);

        // Least significant 64 bits: 2 bits variant (10) | 62 bits random
        long lsb = 0L;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (randomBytes[i] & 0xFFL);
        }
        // Clear top 2 bits and set variant to 0b10 (RFC 4122 / 9562 standard variant)
        lsb &= 0x3FFFFFFFFFFFFFFFL;
        lsb |= 0x8000000000000000L;

        return new UUID(msb, lsb);
    }
}
