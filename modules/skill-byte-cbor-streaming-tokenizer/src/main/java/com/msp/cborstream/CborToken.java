package com.msp.cborstream;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single parsed CBOR data token in the stream.
 */
public record CborToken(
        CborTokenType type,
        CborMajorType majorType,
        Object value,
        long length,
        boolean isIndefinite
) {
    public CborToken {
        Objects.requireNonNull(type, "type cannot be null");
    }

    public static CborToken unsignedInt(long val) {
        return new CborToken(CborTokenType.UNSIGNED_INTEGER, CborMajorType.UNSIGNED_INTEGER, val, val, false);
    }

    public static CborToken unsignedInt(BigInteger val) {
        return new CborToken(CborTokenType.UNSIGNED_INTEGER, CborMajorType.UNSIGNED_INTEGER, val, 0, false);
    }

    public static CborToken negativeInt(long val) {
        return new CborToken(CborTokenType.NEGATIVE_INTEGER, CborMajorType.NEGATIVE_INTEGER, val, 0, false);
    }

    public static CborToken negativeInt(BigInteger val) {
        return new CborToken(CborTokenType.NEGATIVE_INTEGER, CborMajorType.NEGATIVE_INTEGER, val, 0, false);
    }

    public static CborToken byteString(byte[] bytes) {
        return new CborToken(CborTokenType.BYTE_STRING, CborMajorType.BYTE_STRING, bytes, bytes.length, false);
    }

    public static CborToken textString(String text) {
        return new CborToken(CborTokenType.TEXT_STRING, CborMajorType.TEXT_STRING, text, text.length(), false);
    }

    public static CborToken startArray(long length, boolean indefinite) {
        return new CborToken(CborTokenType.START_ARRAY, CborMajorType.ARRAY, null, length, indefinite);
    }

    public static CborToken endArray() {
        return new CborToken(CborTokenType.END_ARRAY, CborMajorType.ARRAY, null, 0, false);
    }

    public static CborToken startMap(long length, boolean indefinite) {
        return new CborToken(CborTokenType.START_MAP, CborMajorType.MAP, null, length, indefinite);
    }

    public static CborToken endMap() {
        return new CborToken(CborTokenType.END_MAP, CborMajorType.MAP, null, 0, false);
    }

    public static CborToken tag(long tagNumber) {
        return new CborToken(CborTokenType.TAG, CborMajorType.TAG, tagNumber, tagNumber, false);
    }

    public static CborToken bool(boolean b) {
        return new CborToken(CborTokenType.BOOLEAN, CborMajorType.SIMPLE_OR_FLOAT, b, 0, false);
    }

    public static CborToken nullValue() {
        return new CborToken(CborTokenType.NULL, CborMajorType.SIMPLE_OR_FLOAT, null, 0, false);
    }

    public static CborToken undefinedValue() {
        return new CborToken(CborTokenType.UNDEFINED, CborMajorType.SIMPLE_OR_FLOAT, null, 0, false);
    }

    public static CborToken floatValue(double d) {
        return new CborToken(CborTokenType.FLOAT, CborMajorType.SIMPLE_OR_FLOAT, d, 0, false);
    }

    public static CborToken simpleValue(int simple) {
        return new CborToken(CborTokenType.SIMPLE_VALUE, CborMajorType.SIMPLE_OR_FLOAT, simple, simple, false);
    }

    public static CborToken breakToken() {
        return new CborToken(CborTokenType.BREAK, CborMajorType.SIMPLE_OR_FLOAT, null, 0, false);
    }

    public static CborToken endOfStream() {
        return new CborToken(CborTokenType.END_OF_STREAM, null, null, 0, false);
    }

    public long longValue() {
        if (value instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalStateException("Token does not hold an integer value: " + value);
    }

    public double doubleValue() {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalStateException("Token does not hold a numeric value: " + value);
    }

    public boolean booleanValue() {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new IllegalStateException("Token does not hold a boolean value: " + value);
    }

    public String textValue() {
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalStateException("Token does not hold a string value: " + value);
    }

    public byte[] bytesValue() {
        if (value instanceof byte[] b) {
            return b;
        }
        throw new IllegalStateException("Token does not hold a byte array value: " + value);
    }

    public long tagValue() {
        if (type == CborTokenType.TAG && value instanceof Long l) {
            return l;
        }
        throw new IllegalStateException("Token is not a TAG: " + type);
    }
}
