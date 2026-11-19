package com.msp.cborstream;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * High-performance streaming pull-parser / tokenizer for RFC 8949 CBOR binary streams.
 */
public class CborTokenizer implements AutoCloseable {

    private final InputStream in;
    private CborToken peeked;

    public CborTokenizer(InputStream in) {
        this.in = Objects.requireNonNull(in, "in cannot be null");
    }

    public static CborTokenizer fromBytes(byte[] bytes) {
        return new CborTokenizer(new ByteArrayInputStream(bytes));
    }

    public boolean hasNext() throws IOException {
        if (peeked != null) {
            return peeked.type() != CborTokenType.END_OF_STREAM;
        }
        peeked = readNextToken();
        return peeked.type() != CborTokenType.END_OF_STREAM;
    }

    public CborToken peekToken() throws IOException {
        if (peeked == null) {
            peeked = readNextToken();
        }
        return peeked;
    }

    public CborToken nextToken() throws IOException {
        if (peeked != null) {
            CborToken token = peeked;
            peeked = null;
            return token;
        }
        return readNextToken();
    }

    private CborToken readNextToken() throws IOException {
        int b = in.read();
        if (b == -1) {
            return CborToken.endOfStream();
        }

        int major = (b >> 5) & 0x07;
        int info = b & 0x1F;
        CborMajorType majorType = CborMajorType.of(major);

        return switch (majorType) {
            case UNSIGNED_INTEGER -> decodeUnsignedInt(info);
            case NEGATIVE_INTEGER -> decodeNegativeInt(info);
            case BYTE_STRING -> decodeByteString(info);
            case TEXT_STRING -> decodeTextString(info);
            case ARRAY -> decodeArray(info);
            case MAP -> decodeMap(info);
            case TAG -> decodeTag(info);
            case SIMPLE_OR_FLOAT -> decodeSimpleOrFloat(info);
        };
    }

    private CborToken decodeUnsignedInt(int info) throws IOException {
        if (info < 24) {
            return CborToken.unsignedInt(info);
        } else if (info == 24) {
            return CborToken.unsignedInt(readUint8());
        } else if (info == 25) {
            return CborToken.unsignedInt(readUint16());
        } else if (info == 26) {
            return CborToken.unsignedInt(readUint32());
        } else if (info == 27) {
            long val = readInt64();
            if (val >= 0) {
                return CborToken.unsignedInt(val);
            } else {
                // Unsigned 64-bit integer exceeding Long.MAX_VALUE
                byte[] raw = longToBytes(val);
                return CborToken.unsignedInt(new BigInteger(1, raw));
            }
        } else {
            throw new IOException("Invalid unsigned int additional info: " + info);
        }
    }

    private CborToken decodeNegativeInt(int info) throws IOException {
        if (info < 24) {
            return CborToken.negativeInt(-1L - info);
        } else if (info == 24) {
            return CborToken.negativeInt(-1L - readUint8());
        } else if (info == 25) {
            return CborToken.negativeInt(-1L - readUint16());
        } else if (info == 26) {
            return CborToken.negativeInt(-1L - readUint32());
        } else if (info == 27) {
            long val = readInt64();
            if (val >= 0) {
                return CborToken.negativeInt(-1L - val);
            } else {
                byte[] raw = longToBytes(val);
                BigInteger unsigned = new BigInteger(1, raw);
                BigInteger neg = BigInteger.valueOf(-1).subtract(unsigned);
                return CborToken.negativeInt(neg);
            }
        } else {
            throw new IOException("Invalid negative int additional info: " + info);
        }
    }

    private CborToken decodeByteString(int info) throws IOException {
        if (info == 31) {
            // Indefinite length byte string
            return new CborToken(CborTokenType.BYTE_STRING, CborMajorType.BYTE_STRING, null, -1, true);
        }
        long length = readLength(info);
        if (length > Integer.MAX_VALUE) {
            throw new IOException("Byte string length too large: " + length);
        }
        byte[] bytes = readExactNBytes((int) length);
        return CborToken.byteString(bytes);
    }

    private CborToken decodeTextString(int info) throws IOException {
        if (info == 31) {
            // Indefinite length text string
            return new CborToken(CborTokenType.TEXT_STRING, CborMajorType.TEXT_STRING, null, -1, true);
        }
        long length = readLength(info);
        if (length > Integer.MAX_VALUE) {
            throw new IOException("Text string length too large: " + length);
        }
        byte[] bytes = readExactNBytes((int) length);
        String text = new String(bytes, StandardCharsets.UTF_8);
        return CborToken.textString(text);
    }

    private CborToken decodeArray(int info) throws IOException {
        if (info == 31) {
            return CborToken.startArray(-1, true);
        }
        long length = readLength(info);
        return CborToken.startArray(length, false);
    }

    private CborToken decodeMap(int info) throws IOException {
        if (info == 31) {
            return CborToken.startMap(-1, true);
        }
        long length = readLength(info);
        return CborToken.startMap(length, false);
    }

    private CborToken decodeTag(int info) throws IOException {
        long tag = readLength(info);
        return CborToken.tag(tag);
    }

    private CborToken decodeSimpleOrFloat(int info) throws IOException {
        return switch (info) {
            case 20 -> CborToken.bool(false);
            case 21 -> CborToken.bool(true);
            case 22 -> CborToken.nullValue();
            case 23 -> CborToken.undefinedValue();
            case 24 -> CborToken.simpleValue(readUint8());
            case 25 -> CborToken.floatValue(decodeHalfFloat(readUint16()));
            case 26 -> CborToken.floatValue(Float.intBitsToFloat((int) readUint32()));
            case 27 -> CborToken.floatValue(Double.longBitsToDouble(readInt64()));
            case 31 -> CborToken.breakToken();
            default -> {
                if (info < 20) {
                    yield CborToken.simpleValue(info);
                }
                throw new IOException("Unsupported simple or float info: " + info);
            }
        };
    }

    private long readLength(int info) throws IOException {
        if (info < 24) {
            return info;
        } else if (info == 24) {
            return readUint8();
        } else if (info == 25) {
            return readUint16();
        } else if (info == 26) {
            return readUint32();
        } else if (info == 27) {
            long l = readInt64();
            if (l < 0) {
                throw new IOException("Length exceeds maximum signed 64-bit value: " + l);
            }
            return l;
        }
        throw new IOException("Invalid length additional info: " + info);
    }

    private int readUint8() throws IOException {
        int b = in.read();
        if (b == -1) throw new EOFException("Unexpected end of CBOR stream");
        return b;
    }

    private int readUint16() throws IOException {
        byte[] b = readExactNBytes(2);
        return ((b[0] & 0xFF) << 8) | (b[1] & 0xFF);
    }

    private long readUint32() throws IOException {
        byte[] b = readExactNBytes(4);
        return (((long) (b[0] & 0xFF)) << 24) |
                (((long) (b[1] & 0xFF)) << 16) |
                (((long) (b[2] & 0xFF)) << 8) |
                ((long) (b[3] & 0xFF));
    }

    private long readInt64() throws IOException {
        byte[] b = readExactNBytes(8);
        return (((long) (b[0] & 0xFF)) << 56) |
                (((long) (b[1] & 0xFF)) << 48) |
                (((long) (b[2] & 0xFF)) << 40) |
                (((long) (b[3] & 0xFF)) << 32) |
                (((long) (b[4] & 0xFF)) << 24) |
                (((long) (b[5] & 0xFF)) << 16) |
                (((long) (b[6] & 0xFF)) << 8) |
                ((long) (b[7] & 0xFF));
    }

    private byte[] readExactNBytes(int n) throws IOException {
        byte[] data = new byte[n];
        int read = 0;
        while (read < n) {
            int count = in.read(data, read, n - read);
            if (count == -1) {
                throw new EOFException("Expected " + n + " bytes, but reached end of stream after " + read + " bytes");
            }
            read += count;
        }
        return data;
    }

    private static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    /**
     * Decodes IEEE 754 16-bit half precision float bits to a standard double.
     */
    public static double decodeHalfFloat(int bits) {
        int s = (bits >> 15) & 0x01;
        int e = (bits >> 10) & 0x1F;
        int f = bits & 0x03FF;
        if (e == 0) {
            if (f == 0) return (s == 0) ? 0.0 : -0.0;
            return (s == 0 ? 1 : -1) * Math.scalb((double) f / 1024.0, -14);
        } else if (e == 31) {
            if (f == 0) return (s == 0) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            return Double.NaN;
        } else {
            return (s == 0 ? 1 : -1) * Math.scalb(1.0 + (double) f / 1024.0, e - 15);
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
