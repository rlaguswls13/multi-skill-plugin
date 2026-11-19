package com.msp.cose;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight RFC 8949 CBOR Decoder in Pure Java 21.
 */
public final class CborReader {

    private final byte[] buffer;
    private int pos;

    public CborReader(byte[] buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer cannot be null");
        this.pos = 0;
    }

    public static CborValue read(byte[] buffer) {
        return new CborReader(buffer).readValue();
    }

    public static CborValue readHex(String hex) {
        return read(hexToBytes(hex));
    }

    public boolean hasRemaining() {
        return pos < buffer.length;
    }

    public int getPosition() {
        return pos;
    }

    public CborValue readValue() {
        if (pos >= buffer.length) {
            throw new CborParseException("Unexpected end of CBOR input at offset " + pos);
        }

        int initialByte = buffer[pos++] & 0xff;
        int majorType = initialByte >>> 5;
        int additionalInfo = initialByte & 0x1f;

        long lengthOrValue = readLengthOrValue(additionalInfo);

        return switch (majorType) {
            case 0 -> new CborValue.IntegerVal(lengthOrValue);
            case 1 -> new CborValue.IntegerVal(-1 - lengthOrValue);
            case 2 -> {
                int len = checkLength(lengthOrValue);
                byte[] bytes = readBytes(len);
                yield new CborValue.ByteStringVal(bytes);
            }
            case 3 -> {
                int len = checkLength(lengthOrValue);
                byte[] bytes = readBytes(len);
                yield new CborValue.TextStringVal(new String(bytes, StandardCharsets.UTF_8));
            }
            case 4 -> {
                int len = checkLength(lengthOrValue);
                List<CborValue> list = new ArrayList<>(Math.min(len, 1024));
                for (int i = 0; i < len; i++) {
                    list.add(readValue());
                }
                yield new CborValue.ArrayVal(list);
            }
            case 5 -> {
                int len = checkLength(lengthOrValue);
                Map<CborValue, CborValue> map = new LinkedHashMap<>();
                for (int i = 0; i < len; i++) {
                    CborValue k = readValue();
                    CborValue v = readValue();
                    map.put(k, v);
                }
                yield new CborValue.MapVal(map);
            }
            case 6 -> {
                CborValue target = readValue();
                yield new CborValue.TagVal(lengthOrValue, target);
            }
            case 7 -> {
                if (additionalInfo == 20) yield new CborValue.BooleanVal(false);
                if (additionalInfo == 21) yield new CborValue.BooleanVal(true);
                if (additionalInfo == 22) yield new CborValue.NullVal();
                throw new CborParseException("Unsupported simple value in major type 7: " + additionalInfo);
            }
            default -> throw new CborParseException("Unknown CBOR major type: " + majorType);
        };
    }

    private long readLengthOrValue(int additionalInfo) {
        if (additionalInfo < 24) {
            return additionalInfo;
        } else if (additionalInfo == 24) {
            return readUint8();
        } else if (additionalInfo == 25) {
            return readUint16();
        } else if (additionalInfo == 26) {
            return readUint32();
        } else if (additionalInfo == 27) {
            return readUint64();
        } else {
            throw new CborParseException("Indefinite length or reserved additional info " + additionalInfo + " not supported");
        }
    }

    private int readUint8() {
        if (pos >= buffer.length) {
            throw new CborParseException("Unexpected EOF reading uint8");
        }
        return buffer[pos++] & 0xff;
    }

    private int readUint16() {
        if (pos + 2 > buffer.length) {
            throw new CborParseException("Unexpected EOF reading uint16");
        }
        return ((buffer[pos++] & 0xff) << 8) | (buffer[pos++] & 0xff);
    }

    private long readUint32() {
        if (pos + 4 > buffer.length) {
            throw new CborParseException("Unexpected EOF reading uint32");
        }
        long val = 0;
        for (int i = 0; i < 4; i++) {
            val = (val << 8) | (buffer[pos++] & 0xffL);
        }
        return val;
    }

    private long readUint64() {
        if (pos + 8 > buffer.length) {
            throw new CborParseException("Unexpected EOF reading uint64");
        }
        long val = 0;
        for (int i = 0; i < 8; i++) {
            val = (val << 8) | (buffer[pos++] & 0xffL);
        }
        if (val < 0) {
            throw new CborParseException("64-bit integer overflow in signed representation: " + Long.toUnsignedString(val));
        }
        return val;
    }

    private byte[] readBytes(int len) {
        if (pos + len > buffer.length) {
            throw new CborParseException("Unexpected EOF reading " + len + " bytes at offset " + pos);
        }
        byte[] result = new byte[len];
        System.arraycopy(buffer, pos, result, 0, len);
        pos += len;
        return result;
    }

    private int checkLength(long length) {
        if (length < 0 || length > Integer.MAX_VALUE - 8 || pos + length > buffer.length) {
            throw new CborParseException("Invalid item length: " + length);
        }
        return (int) length;
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null) return new byte[0];
        String clean = hex.replaceAll("[\\s_:-]", "");
        if (clean.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string length must be even");
        }
        byte[] data = new byte[clean.length() / 2];
        for (int i = 0; i < clean.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(clean.charAt(i), 16) << 4)
                    + Character.digit(clean.charAt(i + 1), 16));
        }
        return data;
    }
}
