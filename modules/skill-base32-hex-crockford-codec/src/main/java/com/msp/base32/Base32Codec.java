package com.msp.base32;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * High-performance, zero-dependency Base32 codec supporting:
 * <ul>
 *     <li>RFC 4648 standard Base32</li>
 *     <li>RFC 4648 extended Hex Base32</li>
 *     <li>Douglas Crockford's Base32 with human-error tolerance (I/L->1, O->0, hyphens ignored)</li>
 * </ul>
 */
public final class Base32Codec {

    private static final char PAD = '=';

    private Base32Codec() {
    }

    /**
     * Encodes byte array using RFC 4648 standard Base32 with '=' padding.
     */
    public static String encode(byte[] data) {
        return encode(data, Base32Variant.RFC4648, true);
    }

    /**
     * Encodes byte array using specified variant and its default padding setting.
     */
    public static String encode(byte[] data, Base32Variant variant) {
        Objects.requireNonNull(variant, "Base32Variant cannot be null");
        return encode(data, variant, variant.isDefaultPadding());
    }

    /**
     * Encodes UTF-8 string into Base32.
     */
    public static String encode(String text, Base32Variant variant) {
        Objects.requireNonNull(text, "Text cannot be null");
        return encode(text.getBytes(StandardCharsets.UTF_8), variant);
    }

    /**
     * Encodes byte array into Base32 with custom padding configuration.
     */
    public static String encode(byte[] data, Base32Variant variant, boolean padding) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        Objects.requireNonNull(variant, "Base32Variant cannot be null");

        if (data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(((data.length + 4) / 5) * 8);
        int i = 0;
        int len = data.length;

        while (i + 4 < len) {
            int b0 = data[i++] & 0xFF;
            int b1 = data[i++] & 0xFF;
            int b2 = data[i++] & 0xFF;
            int b3 = data[i++] & 0xFF;
            int b4 = data[i++] & 0xFF;

            sb.append(variant.charAt(b0 >>> 3));
            sb.append(variant.charAt(((b0 & 0x07) << 2) | (b1 >>> 6)));
            sb.append(variant.charAt((b1 >>> 1) & 0x1F));
            sb.append(variant.charAt(((b1 & 0x01) << 4) | (b2 >>> 4)));
            sb.append(variant.charAt(((b2 & 0x0F) << 1) | (b3 >>> 7)));
            sb.append(variant.charAt((b3 >>> 2) & 0x1F));
            sb.append(variant.charAt(((b3 & 0x03) << 3) | (b4 >>> 5)));
            sb.append(variant.charAt(b4 & 0x1F));
        }

        int remainder = len - i;
        if (remainder == 1) {
            int b0 = data[i] & 0xFF;
            sb.append(variant.charAt(b0 >>> 3));
            sb.append(variant.charAt((b0 & 0x07) << 2));
            if (padding) {
                sb.append("======");
            }
        } else if (remainder == 2) {
            int b0 = data[i++] & 0xFF;
            int b1 = data[i] & 0xFF;
            sb.append(variant.charAt(b0 >>> 3));
            sb.append(variant.charAt(((b0 & 0x07) << 2) | (b1 >>> 6)));
            sb.append(variant.charAt((b1 >>> 1) & 0x1F));
            sb.append(variant.charAt((b1 & 0x01) << 4));
            if (padding) {
                sb.append("====");
            }
        } else if (remainder == 3) {
            int b0 = data[i++] & 0xFF;
            int b1 = data[i++] & 0xFF;
            int b2 = data[i] & 0xFF;
            sb.append(variant.charAt(b0 >>> 3));
            sb.append(variant.charAt(((b0 & 0x07) << 2) | (b1 >>> 6)));
            sb.append(variant.charAt((b1 >>> 1) & 0x1F));
            sb.append(variant.charAt(((b1 & 0x01) << 4) | (b2 >>> 4)));
            sb.append(variant.charAt((b2 & 0x0F) << 1));
            if (padding) {
                sb.append("===");
            }
        } else if (remainder == 4) {
            int b0 = data[i++] & 0xFF;
            int b1 = data[i++] & 0xFF;
            int b2 = data[i++] & 0xFF;
            int b3 = data[i] & 0xFF;
            sb.append(variant.charAt(b0 >>> 3));
            sb.append(variant.charAt(((b0 & 0x07) << 2) | (b1 >>> 6)));
            sb.append(variant.charAt((b1 >>> 1) & 0x1F));
            sb.append(variant.charAt(((b1 & 0x01) << 4) | (b2 >>> 4)));
            sb.append(variant.charAt(((b2 & 0x0F) << 1) | (b3 >>> 7)));
            sb.append(variant.charAt((b3 >>> 2) & 0x1F));
            sb.append(variant.charAt((b3 & 0x03) << 3));
            if (padding) {
                sb.append("=");
            }
        }

        return sb.toString();
    }

    /**
     * Decodes RFC 4648 standard Base32 encoded text.
     */
    public static byte[] decode(String text) {
        return decode(text, Base32Variant.RFC4648);
    }

    /**
     * Decodes Base32 encoded text using the given variant.
     */
    public static byte[] decode(String text, Base32Variant variant) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        Objects.requireNonNull(variant, "Base32Variant cannot be null");

        String cleaned = preprocess(text, variant);
        if (cleaned.isEmpty()) {
            return new byte[0];
        }

        int remainder = cleaned.length() % 8;
        if (remainder == 1 || remainder == 3 || remainder == 6) {
            throw new IllegalArgumentException("Invalid Base32 unpadded length: " + cleaned.length());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream((cleaned.length() * 5) / 8);
        int i = 0;
        int len = cleaned.length();

        while (i + 7 < len) {
            int c0 = decodeVal(cleaned.charAt(i++), variant);
            int c1 = decodeVal(cleaned.charAt(i++), variant);
            int c2 = decodeVal(cleaned.charAt(i++), variant);
            int c3 = decodeVal(cleaned.charAt(i++), variant);
            int c4 = decodeVal(cleaned.charAt(i++), variant);
            int c5 = decodeVal(cleaned.charAt(i++), variant);
            int c6 = decodeVal(cleaned.charAt(i++), variant);
            int c7 = decodeVal(cleaned.charAt(i++), variant);

            out.write((c0 << 3) | (c1 >>> 2));
            out.write(((c1 & 0x03) << 6) | (c2 << 1) | (c3 >>> 4));
            out.write(((c3 & 0x0F) << 4) | (c4 >>> 1));
            out.write(((c4 & 0x01) << 7) | (c5 << 2) | (c6 >>> 3));
            out.write(((c6 & 0x07) << 5) | c7);
        }

        int rem = len - i;
        if (rem == 2) {
            int c0 = decodeVal(cleaned.charAt(i++), variant);
            int c1 = decodeVal(cleaned.charAt(i++), variant);
            out.write((c0 << 3) | (c1 >>> 2));
        } else if (rem == 4) {
            int c0 = decodeVal(cleaned.charAt(i++), variant);
            int c1 = decodeVal(cleaned.charAt(i++), variant);
            int c2 = decodeVal(cleaned.charAt(i++), variant);
            int c3 = decodeVal(cleaned.charAt(i++), variant);
            out.write((c0 << 3) | (c1 >>> 2));
            out.write(((c1 & 0x03) << 6) | (c2 << 1) | (c3 >>> 4));
        } else if (rem == 5) {
            int c0 = decodeVal(cleaned.charAt(i++), variant);
            int c1 = decodeVal(cleaned.charAt(i++), variant);
            int c2 = decodeVal(cleaned.charAt(i++), variant);
            int c3 = decodeVal(cleaned.charAt(i++), variant);
            int c4 = decodeVal(cleaned.charAt(i++), variant);
            out.write((c0 << 3) | (c1 >>> 2));
            out.write(((c1 & 0x03) << 6) | (c2 << 1) | (c3 >>> 4));
            out.write(((c3 & 0x0F) << 4) | (c4 >>> 1));
        } else if (rem == 7) {
            int c0 = decodeVal(cleaned.charAt(i++), variant);
            int c1 = decodeVal(cleaned.charAt(i++), variant);
            int c2 = decodeVal(cleaned.charAt(i++), variant);
            int c3 = decodeVal(cleaned.charAt(i++), variant);
            int c4 = decodeVal(cleaned.charAt(i++), variant);
            int c5 = decodeVal(cleaned.charAt(i++), variant);
            int c6 = decodeVal(cleaned.charAt(i++), variant);
            out.write((c0 << 3) | (c1 >>> 2));
            out.write(((c1 & 0x03) << 6) | (c2 << 1) | (c3 >>> 4));
            out.write(((c3 & 0x0F) << 4) | (c4 >>> 1));
            out.write(((c4 & 0x01) << 7) | (c5 << 2) | (c6 >>> 3));
        }

        return out.toByteArray();
    }

    private static String preprocess(String text, Base32Variant variant) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == PAD) {
                // trailing padding handled
                continue;
            }
            if (variant == Base32Variant.CROCKFORD) {
                if (c == '-') {
                    continue;
                }
                if (c == 'i' || c == 'I' || c == 'l' || c == 'L') {
                    c = '1';
                } else if (c == 'o' || c == 'O') {
                    c = '0';
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static int decodeVal(char c, Base32Variant variant) {
        int val = variant.decodeChar(c);
        if (val < 0) {
            throw new IllegalArgumentException("Illegal character in Base32 string: '" + c + "'");
        }
        return val;
    }
}
