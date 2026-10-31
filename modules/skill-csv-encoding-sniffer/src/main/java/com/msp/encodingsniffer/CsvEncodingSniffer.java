package com.msp.encodingsniffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Zero-dependency encoding detector for CSV and text streams.
 * <p>
 * Sniffs byte-order marks (BOM) for UTF-8, UTF-16LE, UTF-16BE, UTF-32LE, UTF-32BE,
 * and performs heuristic sequence verification for UTF-8, Korean CP949/EUC-KR, US-ASCII,
 * and ISO-8859-1 fallback.
 */
public final class CsvEncodingSniffer {

    public static final int DEFAULT_SAMPLE_LIMIT = 8192;

    public static final Charset CHARSET_EUC_KR;
    public static final Charset CHARSET_MS949;
    public static final Charset CHARSET_UTF_32BE;
    public static final Charset CHARSET_UTF_32LE;

    static {
        Charset euckr;
        try {
            euckr = Charset.forName("EUC-KR");
        } catch (Exception e) {
            euckr = StandardCharsets.ISO_8859_1;
        }
        CHARSET_EUC_KR = euckr;

        Charset ms949;
        try {
            ms949 = Charset.forName("MS949");
        } catch (Exception e) {
            try {
                ms949 = Charset.forName("x-windows-949");
            } catch (Exception ex) {
                ms949 = CHARSET_EUC_KR;
            }
        }
        CHARSET_MS949 = ms949;

        Charset u32be;
        try {
            u32be = Charset.forName("UTF-32BE");
        } catch (Exception e) {
            u32be = StandardCharsets.UTF_16BE;
        }
        CHARSET_UTF_32BE = u32be;

        Charset u32le;
        try {
            u32le = Charset.forName("UTF-32LE");
        } catch (Exception e) {
            u32le = StandardCharsets.UTF_16LE;
        }
        CHARSET_UTF_32LE = u32le;
    }

    private CsvEncodingSniffer() {
        // Utility class
    }

    /**
     * Detects the character encoding of a given byte array sample.
     *
     * @param sampleBytes byte array representing text or CSV content
     * @return the {@link EncodingConfidence} report
     */
    public static EncodingConfidence detect(byte[] sampleBytes) {
        if (sampleBytes == null || sampleBytes.length == 0) {
            return new EncodingConfidence(StandardCharsets.UTF_8, 1.0, false);
        }

        // 1. Check for BOM headers
        EncodingConfidence bomConfidence = checkBom(sampleBytes);
        if (bomConfidence != null) {
            return bomConfidence;
        }

        // 2. Check for pure 7-bit US-ASCII
        if (isPureAscii(sampleBytes)) {
            return new EncodingConfidence(StandardCharsets.US_ASCII, 1.0, false);
        }

        // 3. Check for valid UTF-8 multi-byte sequences
        if (isValidUtf8(sampleBytes)) {
            return new EncodingConfidence(StandardCharsets.UTF_8, 0.99, false);
        }

        // 4. Check for Korean EUC-KR / CP949 (MS949) double-byte sequences
        EncodingConfidence koreanCheck = checkKoreanEncoding(sampleBytes);
        if (koreanCheck != null) {
            return koreanCheck;
        }

        // 5. Fallback to ISO-8859-1 (Latin-1)
        return new EncodingConfidence(StandardCharsets.ISO_8859_1, 0.50, false);
    }

    /**
     * Reads up to {@code sampleLimit} bytes from an {@link InputStream} and determines the most likely Charset.
     *
     * @param is          source input stream
     * @param sampleLimit maximum bytes to inspect
     * @return recommended Charset
     * @throws IOException if reading the stream fails
     */
    public static Charset detectCharset(InputStream is, int sampleLimit) throws IOException {
        Objects.requireNonNull(is, "InputStream must not be null");
        int limit = sampleLimit > 0 ? sampleLimit : DEFAULT_SAMPLE_LIMIT;
        byte[] sample = is.readNBytes(limit);
        return detect(sample).charset();
    }

    /**
     * Reads up to the default 8KB sample from an {@link InputStream} and determines the Charset.
     */
    public static Charset detectCharset(InputStream is) throws IOException {
        return detectCharset(is, DEFAULT_SAMPLE_LIMIT);
    }

    private static EncodingConfidence checkBom(byte[] bytes) {
        int len = bytes.length;
        if (len >= 4) {
            // UTF-32BE: 00 00 FE FF
            if (bytes[0] == 0x00 && bytes[1] == 0x00 && (bytes[2] & 0xFF) == 0xFE && (bytes[3] & 0xFF) == 0xFF) {
                return new EncodingConfidence(CHARSET_UTF_32BE, 1.0, true);
            }
            // UTF-32LE: FF FE 00 00
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE && bytes[2] == 0x00 && bytes[3] == 0x00) {
                return new EncodingConfidence(CHARSET_UTF_32LE, 1.0, true);
            }
        }

        if (len >= 3) {
            // UTF-8 BOM: EF BB BF
            if ((bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return new EncodingConfidence(StandardCharsets.UTF_8, 1.0, true);
            }
        }

        if (len >= 2) {
            // UTF-16BE: FE FF
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return new EncodingConfidence(StandardCharsets.UTF_16BE, 1.0, true);
            }
            // UTF-16LE: FF FE
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return new EncodingConfidence(StandardCharsets.UTF_16LE, 1.0, true);
            }
        }

        return null;
    }

    private static boolean isPureAscii(byte[] bytes) {
        for (byte b : bytes) {
            if ((b & 0x80) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static EncodingConfidence checkKoreanEncoding(byte[] bytes) {
        // Try MS949 decoding
        try {
            CharsetDecoder decoder = CHARSET_MS949.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            return null;
        }

        // Verify presence of double-byte Korean character ranges
        int koreanDoubleBytes = 0;
        boolean purelyEucKr = true;

        int i = 0;
        while (i < bytes.length) {
            int b1 = bytes[i] & 0xFF;
            if (b1 < 0x80) {
                i++;
                continue;
            }

            if (i + 1 < bytes.length) {
                int b2 = bytes[i + 1] & 0xFF;
                // EUC-KR range: 0xA1..0xFE and 0xA1..0xFE
                if (b1 >= 0xA1 && b1 <= 0xFE && b2 >= 0xA1 && b2 <= 0xFE) {
                    koreanDoubleBytes++;
                    i += 2;
                    continue;
                }

                // CP949 expanded range:
                // Lead: 0x81..0xFE
                // Trail: 0x41..0x5A, 0x61..0x7A, 0x81..0xFE
                boolean isTrailValid = (b2 >= 0x41 && b2 <= 0x5A) ||
                        (b2 >= 0x61 && b2 <= 0x7A) ||
                        (b2 >= 0x81 && b2 <= 0xFE);
                if (b1 >= 0x81 && b1 <= 0xFE && isTrailValid) {
                    koreanDoubleBytes++;
                    purelyEucKr = false;
                    i += 2;
                    continue;
                }
            }
            // Byte not fitting recognized Korean range
            purelyEucKr = false;
            i++;
        }

        if (koreanDoubleBytes > 0) {
            Charset charset = purelyEucKr ? CHARSET_EUC_KR : CHARSET_MS949;
            return new EncodingConfidence(charset, 0.95, false);
        }

        return null;
    }
}
