package com.msp.cborstream;

/**
 * RFC 8949 CBOR Major Types (0 to 7).
 */
public enum CborMajorType {
    UNSIGNED_INTEGER(0),
    NEGATIVE_INTEGER(1),
    BYTE_STRING(2),
    TEXT_STRING(3),
    ARRAY(4),
    MAP(5),
    TAG(6),
    SIMPLE_OR_FLOAT(7);

    private final int value;

    CborMajorType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static CborMajorType of(int value) {
        for (CborMajorType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown CBOR major type: " + value);
    }
}
