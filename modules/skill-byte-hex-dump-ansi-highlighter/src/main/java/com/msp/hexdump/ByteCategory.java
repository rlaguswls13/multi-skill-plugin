package com.msp.hexdump;

/**
 * Categorization of byte values for semantic syntax highlighting.
 */
public enum ByteCategory {
    NULL_BYTE,
    PRINTABLE_ASCII,
    CONTROL_CHAR,
    HIGH_BINARY;

    public static ByteCategory of(int byteValue) {
        int unsigned = byteValue & 0xFF;
        if (unsigned == 0x00) {
            return NULL_BYTE;
        }
        if (unsigned >= 0x20 && unsigned <= 0x7E) {
            return PRINTABLE_ASCII;
        }
        if ((unsigned >= 0x01 && unsigned <= 0x1F) || unsigned == 0x7F) {
            return CONTROL_CHAR;
        }
        return HIGH_BINARY;
    }
}
