package com.msp.ocsp;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

/**
 * Internal pure-Java ASN.1 DER reader and writer for RFC 6960 OCSP structures.
 */
final class DerCodec {

    static final int TAG_INTEGER = 0x02;
    static final int TAG_BIT_STRING = 0x03;
    static final int TAG_OCTET_STRING = 0x04;
    static final int TAG_NULL = 0x05;
    static final int TAG_OID = 0x06;
    static final int TAG_ENUMERATED = 0x0A;
    static final int TAG_GENERALIZED_TIME = 0x18;
    static final int TAG_SEQUENCE = 0x30;

    static final int TAG_CTX_PRIMITIVE_0 = 0x80;
    static final int TAG_CTX_PRIMITIVE_2 = 0x82;
    static final int TAG_CTX_CONSTRUCTED_0 = 0xA0;
    static final int TAG_CTX_CONSTRUCTED_1 = 0xA1;
    static final int TAG_CTX_CONSTRUCTED_2 = 0xA2;

    private static final DateTimeFormatter GEN_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    private DerCodec() {
    }

    record Tlv(int tag, byte[] content, int startOffset, int endOffset) {
        byte[] rawSlice(byte[] source) {
            return Arrays.copyOfRange(source, startOffset, endOffset);
        }
    }

    static final class Reader {
        private final byte[] data;
        private int pos;
        private final int limit;

        Reader(byte[] data) {
            this(data, 0, data.length);
        }

        Reader(byte[] data, int offset, int length) {
            this.data = data;
            this.pos = offset;
            this.limit = offset + length;
        }

        boolean hasRemaining() {
            return pos < limit;
        }

        int peekTag() {
            if (!hasRemaining()) {
                throw new OcspParseException("Unexpected end of DER stream");
            }
            return data[pos] & 0xFF;
        }

        Tlv readTlv() {
            if (!hasRemaining()) {
                throw new OcspParseException("Unexpected end of DER stream");
            }
            int start = pos;
            int tag = data[pos++] & 0xFF;
            if ((tag & 0x1F) == 0x1F) {
                throw new OcspParseException("High-tag-number form not supported in OCSP DER: 0x" + Integer.toHexString(tag));
            }
            int length = readLength();
            if (pos + length > limit) {
                throw new OcspParseException("DER element length " + length + " exceeds remaining bytes " + (limit - pos));
            }
            byte[] content = Arrays.copyOfRange(data, pos, pos + length);
            pos += length;
            return new Tlv(tag, content, start, pos);
        }

        Tlv readExpected(int expectedTag, String context) {
            Tlv tlv = readTlv();
            if (tlv.tag() != expectedTag) {
                throw new OcspParseException(
                        "Expected DER tag 0x" + Integer.toHexString(expectedTag)
                                + " for " + context + " but found 0x" + Integer.toHexString(tlv.tag()));
            }
            return tlv;
        }

        void ensureExhausted(String context) {
            if (hasRemaining()) {
                throw new OcspParseException("Trailing " + (limit - pos) + " bytes in " + context);
            }
        }

        private int readLength() {
            if (!hasRemaining()) {
                throw new OcspParseException("Truncated DER length");
            }
            int first = data[pos++] & 0xFF;
            if (first < 0x80) {
                return first;
            }
            if (first == 0x80) {
                throw new OcspParseException("Indefinite length encoding is prohibited in DER");
            }
            int numBytes = first & 0x7F;
            if (numBytes > 4) {
                throw new OcspParseException("DER length too large: " + numBytes + " bytes");
            }
            if (pos + numBytes > limit) {
                throw new OcspParseException("Truncated DER long-form length");
            }
            int length = 0;
            for (int i = 0; i < numBytes; i++) {
                length = (length << 8) | (data[pos++] & 0xFF);
            }
            if (length < 0) {
                throw new OcspParseException("Negative DER length overflow");
            }
            return length;
        }
    }

    // ---- Decoders ----

    static int decodeEnumerated(byte[] content) {
        if (content.length == 0 || content.length > 4) {
            throw new OcspParseException("Invalid ENUMERATED length: " + content.length);
        }
        return new BigInteger(content).intValueExact();
    }

    static BigInteger decodeInteger(byte[] content) {
        if (content.length == 0) {
            throw new OcspParseException("Empty INTEGER content");
        }
        return new BigInteger(content);
    }

    static String decodeOid(byte[] content) {
        if (content.length == 0) {
            throw new OcspParseException("Empty OBJECT IDENTIFIER");
        }
        int first = content[0] & 0xFF;
        int arc0 = Math.min(first / 40, 2);
        int arc1 = first - arc0 * 40;
        StringBuilder sb = new StringBuilder();
        sb.append(arc0).append('.').append(arc1);

        long value = 0;
        boolean inArc = false;
        for (int i = 1; i < content.length; i++) {
            int b = content[i] & 0xFF;
            value = (value << 7) | (b & 0x7F);
            inArc = true;
            if ((b & 0x80) == 0) {
                sb.append('.').append(value);
                value = 0;
                inArc = false;
            }
        }
        if (inArc) {
            throw new OcspParseException("Truncated OID arc");
        }
        return sb.toString();
    }

    static Instant decodeGeneralizedTime(byte[] content) {
        String s = new String(content, StandardCharsets.US_ASCII);
        if (!s.endsWith("Z") || s.length() < 15) {
            throw new OcspParseException("Invalid DER GeneralizedTime (must be UTC ending in 'Z'): " + s);
        }
        try {
            int dot = s.indexOf('.');
            if (dot < 0) {
                if (s.length() != 15) {
                    throw new OcspParseException("Invalid GeneralizedTime format: " + s);
                }
                LocalDateTime ldt = LocalDateTime.parse(s.substring(0, 14),
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                return ldt.toInstant(ZoneOffset.UTC);
            } else {
                LocalDateTime ldt = LocalDateTime.parse(s.substring(0, 14),
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                String frac = s.substring(dot + 1, s.length() - 1);
                if (frac.isEmpty() || frac.length() > 9) {
                    throw new OcspParseException("Invalid GeneralizedTime fractional seconds: " + s);
                }
                int nanos = Integer.parseInt(frac + "0".repeat(9 - frac.length()));
                return ldt.toInstant(ZoneOffset.UTC).plusNanos(nanos);
            }
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new OcspParseException("Failed to parse GeneralizedTime: " + s, e);
        }
    }

    static byte[] decodeBitString(byte[] content) {
        if (content.length == 0) {
            throw new OcspParseException("Empty BIT STRING");
        }
        int unusedBits = content[0] & 0xFF;
        if (unusedBits > 7) {
            throw new OcspParseException("Invalid BIT STRING unused bits: " + unusedBits);
        }
        return Arrays.copyOfRange(content, 1, content.length);
    }

    // ---- Encoders ----

    static byte[] wrap(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 6);
        out.write(tag);
        writeLength(out, content.length);
        out.writeBytes(content);
        return out.toByteArray();
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            if (p != null) {
                out.writeBytes(p);
            }
        }
        return out.toByteArray();
    }

    static byte[] encodeSequence(byte[]... elements) {
        return wrap(TAG_SEQUENCE, concat(elements));
    }

    static byte[] encodeInteger(BigInteger value) {
        return wrap(TAG_INTEGER, value.toByteArray());
    }

    static byte[] encodeEnumerated(int value) {
        return wrap(TAG_ENUMERATED, BigInteger.valueOf(value).toByteArray());
    }

    static byte[] encodeOctetString(byte[] bytes) {
        return wrap(TAG_OCTET_STRING, bytes);
    }

    static byte[] encodeBitString(byte[] bytes) {
        byte[] buf = new byte[bytes.length + 1];
        buf[0] = 0; // 0 unused bits
        System.arraycopy(bytes, 0, buf, 1, bytes.length);
        return wrap(TAG_BIT_STRING, buf);
    }

    static byte[] encodeNull() {
        return new byte[]{(byte) TAG_NULL, 0x00};
    }

    static byte[] encodeOid(String oid) {
        String[] parts = oid.split("\\.");
        if (parts.length < 2) {
            throw new OcspParseException("Invalid OID: " + oid);
        }
        int arc0 = Integer.parseInt(parts[0]);
        int arc1 = Integer.parseInt(parts[1]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(arc0 * 40 + arc1);
        for (int i = 2; i < parts.length; i++) {
            long val = Long.parseLong(parts[i]);
            if (val == 0) {
                out.write(0);
            } else {
                byte[] buf = new byte[10];
                int idx = buf.length;
                buf[--idx] = (byte) (val & 0x7F);
                val >>>= 7;
                while (val > 0) {
                    buf[--idx] = (byte) ((val & 0x7F) | 0x80);
                    val >>>= 7;
                }
                out.write(buf, idx, buf.length - idx);
            }
        }
        return wrap(TAG_OID, out.toByteArray());
    }

    static byte[] encodeGeneralizedTime(Instant instant) {
        String formatted = GEN_TIME_FMT.format(instant);
        return wrap(TAG_GENERALIZED_TIME, formatted.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] encodeAlgorithmIdentifier(String oid) {
        return encodeSequence(encodeOid(oid), encodeNull());
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 0x80) {
            out.write(length);
        } else if (length <= 0xFF) {
            out.write(0x81);
            out.write(length);
        } else if (length <= 0xFFFF) {
            out.write(0x82);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        } else if (length <= 0xFFFFFF) {
            out.write(0x83);
            out.write((length >>> 16) & 0xFF);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(0x84);
            out.write((length >>> 24) & 0xFF);
            out.write((length >>> 16) & 0xFF);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }
}
