package com.msp.checksum;

/**
 * Streaming interface for calculating CRC checksums incrementally.
 */
public interface CrcChecksum {

    /**
     * Updates the CRC calculation with a single byte.
     *
     * @param b lower 8 bits are used as the input byte
     */
    void update(int b);

    /**
     * Updates the CRC calculation with a slice of a byte array.
     *
     * @param b   byte buffer
     * @param off offset into array
     * @param len number of bytes to process
     */
    void update(byte[] b, int off, int len);

    /**
     * Updates the CRC calculation with all bytes in the array.
     *
     * @param b byte buffer
     */
    default void update(byte[] b) {
        if (b != null) {
            update(b, 0, b.length);
        }
    }

    /**
     * Returns the current computed checksum value.
     * For 16-bit algorithms, value fits in 0..65535.
     * For 32-bit algorithms, value fits in 0..4294967295.
     *
     * @return current checksum value
     */
    long getValue();

    /**
     * Resets the checksum calculation back to initial state.
     */
    void reset();

    /**
     * Returns the underlying algorithm.
     *
     * @return CRC algorithm
     */
    CrcAlgorithm getAlgorithm();

    /**
     * Returns the uppercase hexadecimal representation of the current checksum.
     *
     * @return 4 or 8 hex character string
     */
    default String toHexString() {
        if (getAlgorithm().is16Bit()) {
            return MultiCrcCalculator.toHexString16((int) getValue());
        } else {
            return MultiCrcCalculator.toHexString32(getValue());
        }
    }
}
