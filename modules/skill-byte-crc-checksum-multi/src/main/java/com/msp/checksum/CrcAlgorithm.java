package com.msp.checksum;

/**
 * Standard CRC algorithms supported by {@link MultiCrcCalculator}.
 */
public enum CrcAlgorithm {

    /**
     * CRC-16/MODBUS: Poly 0x8005 (reversed 0xA001), Init 0xFFFF, RefIn/RefOut true, XorOut 0x0000.
     * Check vector "123456789" = 0x4B37.
     */
    CRC16_MODBUS(16, 0x8005, 0xFFFF, 0x0000, true, true),

    /**
     * CRC-16/CCITT-FALSE (commonly referenced as CCITT): Poly 0x1021, Init 0xFFFF, RefIn/RefOut false, XorOut 0x0000.
     * Check vector "123456789" = 0x29B1.
     */
    CRC16_CCITT(16, 0x1021, 0xFFFF, 0x0000, false, false),

    /**
     * CRC-16/XMODEM: Poly 0x1021, Init 0x0000, RefIn/RefOut false, XorOut 0x0000.
     * Check vector "123456789" = 0x31C3.
     */
    CRC16_XMODEM(16, 0x1021, 0x0000, 0x0000, false, false),

    /**
     * CRC-32/ISO-HDLC (IEEE 802.3, standard Java java.util.zip.CRC32):
     * Poly 0x04C11DB7, Init 0xFFFFFFFF, RefIn/RefOut true, XorOut 0xFFFFFFFF.
     * Check vector "123456789" = 0xCBF43926 (3421780262).
     */
    CRC32_IEEE(32, 0x04C11DB7L, 0xFFFFFFFFL, 0xFFFFFFFFL, true, true),

    /**
     * CRC-32C/Castagnoli (iSCSI, SCTP): Poly 0x1EDC6F41, Init 0xFFFFFFFF, RefIn/RefOut true, XorOut 0xFFFFFFFF.
     * Check vector "123456789" = 0xE3069283 (3808859779).
     */
    CRC32_CASTAGNOLI(32, 0x1EDC6F41L, 0xFFFFFFFFL, 0xFFFFFFFFL, true, true);

    private final int bitWidth;
    private final long polynomial;
    private final long initialValue;
    private final long finalXor;
    private final boolean refIn;
    private final boolean refOut;

    CrcAlgorithm(int bitWidth, long polynomial, long initialValue, long finalXor, boolean refIn, boolean refOut) {
        this.bitWidth = bitWidth;
        this.polynomial = polynomial;
        this.initialValue = initialValue;
        this.finalXor = finalXor;
        this.refIn = refIn;
        this.refOut = refOut;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public long getPolynomial() {
        return polynomial;
    }

    public long getInitialValue() {
        return initialValue;
    }

    public long getFinalXor() {
        return finalXor;
    }

    public boolean isRefIn() {
        return refIn;
    }

    public boolean isRefOut() {
        return refOut;
    }

    public boolean is16Bit() {
        return bitWidth == 16;
    }

    public boolean is32Bit() {
        return bitWidth == 32;
    }
}
