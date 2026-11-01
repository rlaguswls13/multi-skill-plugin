package com.msp.checksum;

import java.util.Objects;

/**
 * Pure Java multi-standard CRC-16 and CRC-32/CRC-32C bitwise checksum calculator.
 *
 * <p>Supports table-accelerated computation for MODBUS, CCITT, XMODEM, IEEE 802.3,
 * and Castagnoli algorithms.</p>
 */
public final class MultiCrcCalculator {

    private static final int[] TABLE_MODBUS = new int[256];
    private static final int[] TABLE_CCITT = new int[256];
    private static final long[] TABLE_IEEE = new long[256];
    private static final long[] TABLE_CASTAGNOLI = new long[256];

    static {
        // Precompute CRC-16 MODBUS (reversed poly 0xA001)
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
            TABLE_MODBUS[i] = crc & 0xFFFF;
        }

        // Precompute CRC-16 CCITT / XMODEM (poly 0x1021)
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
            TABLE_CCITT[i] = crc & 0xFFFF;
        }

        // Precompute CRC-32 IEEE (reversed poly 0xEDB88320)
        for (int i = 0; i < 256; i++) {
            long crc = i;
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xEDB88320L;
                } else {
                    crc >>>= 1;
                }
            }
            TABLE_IEEE[i] = crc & 0xFFFFFFFFL;
        }

        // Precompute CRC-32C Castagnoli (reversed poly 0x82F63B78)
        for (int i = 0; i < 256; i++) {
            long crc = i;
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0x82F63B78L;
                } else {
                    crc >>>= 1;
                }
            }
            TABLE_CASTAGNOLI[i] = crc & 0xFFFFFFFFL;
        }
    }

    private MultiCrcCalculator() {
        // Utility class
    }

    /**
     * Computes the 16-bit CRC checksum for a slice of data.
     *
     * @param algorithm 16-bit algorithm (MODBUS, CCITT, XMODEM)
     * @param data      byte array
     * @param off       starting offset
     * @param len       number of bytes
     * @return 16-bit unsigned integer (0..65535)
     */
    public static int compute16(CrcAlgorithm algorithm, byte[] data, int off, int len) {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        if (!algorithm.is16Bit()) {
            throw new IllegalArgumentException("Algorithm " + algorithm + " is not a 16-bit CRC algorithm");
        }
        CrcChecksum checksum = create(algorithm);
        checksum.update(data, off, len);
        return (int) checksum.getValue();
    }

    /**
     * Computes the 16-bit CRC checksum for all bytes in data.
     *
     * @param algorithm 16-bit algorithm
     * @param data      byte array
     * @return 16-bit unsigned integer (0..65535)
     */
    public static int compute16(CrcAlgorithm algorithm, byte[] data) {
        return compute16(algorithm, data, 0, data != null ? data.length : 0);
    }

    /**
     * Computes the 32-bit CRC checksum for a slice of data.
     *
     * @param algorithm 32-bit algorithm (IEEE, CASTAGNOLI)
     * @param data      byte array
     * @param off       starting offset
     * @param len       number of bytes
     * @return 32-bit unsigned long (0..4294967295)
     */
    public static long compute32(CrcAlgorithm algorithm, byte[] data, int off, int len) {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        if (!algorithm.is32Bit()) {
            throw new IllegalArgumentException("Algorithm " + algorithm + " is not a 32-bit CRC algorithm");
        }
        CrcChecksum checksum = create(algorithm);
        checksum.update(data, off, len);
        return checksum.getValue();
    }

    /**
     * Computes the 32-bit CRC checksum for all bytes in data.
     *
     * @param algorithm 32-bit algorithm
     * @param data      byte array
     * @return 32-bit unsigned long (0..4294967295)
     */
    public static long compute32(CrcAlgorithm algorithm, byte[] data) {
        return compute32(algorithm, data, 0, data != null ? data.length : 0);
    }

    /**
     * Formats a 16-bit CRC checksum as an uppercase 4-digit hexadecimal string (e.g., "4B37").
     *
     * @param crc16 16-bit CRC value
     * @return 4-character hex string
     */
    public static String toHexString16(int crc16) {
        return String.format("%04X", crc16 & 0xFFFF);
    }

    /**
     * Formats a 32-bit CRC checksum as an uppercase 8-digit hexadecimal string (e.g., "CBF43926").
     *
     * @param crc32 32-bit CRC value
     * @return 8-character hex string
     */
    public static String toHexString32(long crc32) {
        return String.format("%08X", crc32 & 0xFFFFFFFFL);
    }

    /**
     * Creates a stateful {@link CrcChecksum} instance for the specified algorithm.
     *
     * @param algorithm target CRC algorithm
     * @return newly initialized CrcChecksum instance
     */
    public static CrcChecksum create(CrcAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        return switch (algorithm) {
            case CRC16_MODBUS -> new Crc16ModbusChecksum();
            case CRC16_CCITT -> new Crc16CcittChecksum();
            case CRC16_XMODEM -> new Crc16XmodemChecksum();
            case CRC32_IEEE -> new Crc32IeeeChecksum();
            case CRC32_CASTAGNOLI -> new Crc32CastagnoliChecksum();
        };
    }

    private static void checkBounds(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException("byte array must not be null");
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new IndexOutOfBoundsException("Invalid offset/length: off=" + off + ", len=" + len + ", size=" + b.length);
        }
    }

    private static final class Crc16ModbusChecksum implements CrcChecksum {
        private int crc = 0xFFFF;

        @Override
        public void update(int b) {
            crc = ((crc >>> 8) ^ TABLE_MODBUS[(crc ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        }

        @Override
        public void update(byte[] b, int off, int len) {
            checkBounds(b, off, len);
            int current = crc;
            for (int i = off; i < off + len; i++) {
                current = ((current >>> 8) ^ TABLE_MODBUS[(current ^ (b[i] & 0xFF)) & 0xFF]) & 0xFFFF;
            }
            crc = current;
        }

        @Override
        public long getValue() {
            return crc & 0xFFFF;
        }

        @Override
        public void reset() {
            crc = 0xFFFF;
        }

        @Override
        public CrcAlgorithm getAlgorithm() {
            return CrcAlgorithm.CRC16_MODBUS;
        }
    }

    private static final class Crc16CcittChecksum implements CrcChecksum {
        private int crc = 0xFFFF;

        @Override
        public void update(int b) {
            crc = ((crc << 8) ^ TABLE_CCITT[((crc >>> 8) ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        }

        @Override
        public void update(byte[] b, int off, int len) {
            checkBounds(b, off, len);
            int current = crc;
            for (int i = off; i < off + len; i++) {
                current = ((current << 8) ^ TABLE_CCITT[((current >>> 8) ^ (b[i] & 0xFF)) & 0xFF]) & 0xFFFF;
            }
            crc = current;
        }

        @Override
        public long getValue() {
            return crc & 0xFFFF;
        }

        @Override
        public void reset() {
            crc = 0xFFFF;
        }

        @Override
        public CrcAlgorithm getAlgorithm() {
            return CrcAlgorithm.CRC16_CCITT;
        }
    }

    private static final class Crc16XmodemChecksum implements CrcChecksum {
        private int crc = 0x0000;

        @Override
        public void update(int b) {
            crc = ((crc << 8) ^ TABLE_CCITT[((crc >>> 8) ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        }

        @Override
        public void update(byte[] b, int off, int len) {
            checkBounds(b, off, len);
            int current = crc;
            for (int i = off; i < off + len; i++) {
                current = ((current << 8) ^ TABLE_CCITT[((current >>> 8) ^ (b[i] & 0xFF)) & 0xFF]) & 0xFFFF;
            }
            crc = current;
        }

        @Override
        public long getValue() {
            return crc & 0xFFFF;
        }

        @Override
        public void reset() {
            crc = 0x0000;
        }

        @Override
        public CrcAlgorithm getAlgorithm() {
            return CrcAlgorithm.CRC16_XMODEM;
        }
    }

    private static final class Crc32IeeeChecksum implements CrcChecksum {
        private long crc = 0xFFFFFFFFL;

        @Override
        public void update(int b) {
            crc = ((crc >>> 8) ^ TABLE_IEEE[(int) ((crc ^ (b & 0xFF)) & 0xFF)]) & 0xFFFFFFFFL;
        }

        @Override
        public void update(byte[] b, int off, int len) {
            checkBounds(b, off, len);
            long current = crc;
            for (int i = off; i < off + len; i++) {
                current = ((current >>> 8) ^ TABLE_IEEE[(int) ((current ^ (b[i] & 0xFF)) & 0xFF)]) & 0xFFFFFFFFL;
            }
            crc = current;
        }

        @Override
        public long getValue() {
            return (crc ^ 0xFFFFFFFFL) & 0xFFFFFFFFL;
        }

        @Override
        public void reset() {
            crc = 0xFFFFFFFFL;
        }

        @Override
        public CrcAlgorithm getAlgorithm() {
            return CrcAlgorithm.CRC32_IEEE;
        }
    }

    private static final class Crc32CastagnoliChecksum implements CrcChecksum {
        private long crc = 0xFFFFFFFFL;

        @Override
        public void update(int b) {
            crc = ((crc >>> 8) ^ TABLE_CASTAGNOLI[(int) ((crc ^ (b & 0xFF)) & 0xFF)]) & 0xFFFFFFFFL;
        }

        @Override
        public void update(byte[] b, int off, int len) {
            checkBounds(b, off, len);
            long current = crc;
            for (int i = off; i < off + len; i++) {
                current = ((current >>> 8) ^ TABLE_CASTAGNOLI[(int) ((current ^ (b[i] & 0xFF)) & 0xFF)]) & 0xFFFFFFFFL;
            }
            crc = current;
        }

        @Override
        public long getValue() {
            return (crc ^ 0xFFFFFFFFL) & 0xFFFFFFFFL;
        }

        @Override
        public void reset() {
            crc = 0xFFFFFFFFL;
        }

        @Override
        public CrcAlgorithm getAlgorithm() {
            return CrcAlgorithm.CRC32_CASTAGNOLI;
        }
    }
}
