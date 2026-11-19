package com.msp.tlvcodec;

/**
 * ASN.1 / EMV BER-TLV tag class encoded in the top two bits (bits 8-7) of the first tag octet.
 */
public enum TagClass {
    /** Bits 8-7 = 00 (0x00). Standard ASN.1 universal types. */
    UNIVERSAL(0x00),
    /** Bits 8-7 = 01 (0x40). Application-specific types (e.g. EMV tags 0x6F, 0x70). */
    APPLICATION(0x40),
    /** Bits 8-7 = 10 (0x80). Context-specific types (e.g. EMV tags 0x84, 0x9F26). */
    CONTEXT_SPECIFIC(0x80),
    /** Bits 8-7 = 11 (0xC0). Private-use types. */
    PRIVATE(0xC0);

    private final int mask;

    TagClass(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }

    /**
     * Decodes the {@link TagClass} from the first octet of an encoded BER-TLV tag.
     */
    public static TagClass fromFirstByte(int firstByte) {
        return switch (firstByte & 0xC0) {
            case 0x00 -> UNIVERSAL;
            case 0x40 -> APPLICATION;
            case 0x80 -> CONTEXT_SPECIFIC;
            case 0xC0 -> PRIVATE;
            default -> throw new AssertionError("Unreachable tag class bits");
        };
    }
}
