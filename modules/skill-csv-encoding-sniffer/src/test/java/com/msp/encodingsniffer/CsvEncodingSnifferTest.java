package com.msp.encodingsniffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CsvEncodingSnifferTest {

    @Test
    @DisplayName("Should detect UTF-8 with BOM")
    void testUtf8WithBom() {
        byte[] text = "id,name\n1,Alice".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[3 + text.length];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(text, 0, withBom, 3, text.length);

        EncodingConfidence result = CsvEncodingSniffer.detect(withBom);
        assertEquals(StandardCharsets.UTF_8, result.charset());
        assertEquals(1.0, result.confidence());
        assertTrue(result.hasBom());
    }

    @Test
    @DisplayName("Should detect UTF-16LE and UTF-16BE with BOM")
    void testUtf16Bom() {
        byte[] utf16le = new byte[]{(byte) 0xFF, (byte) 0xFE, 'a', 0x00, 'b', 0x00};
        EncodingConfidence le = CsvEncodingSniffer.detect(utf16le);
        assertEquals(StandardCharsets.UTF_16LE, le.charset());
        assertTrue(le.hasBom());

        byte[] utf16be = new byte[]{(byte) 0xFE, (byte) 0xFF, 0x00, 'a', 0x00, 'b'};
        EncodingConfidence be = CsvEncodingSniffer.detect(utf16be);
        assertEquals(StandardCharsets.UTF_16BE, be.charset());
        assertTrue(be.hasBom());
    }

    @Test
    @DisplayName("Should detect pure 7-bit ASCII as US-ASCII")
    void testAscii() {
        byte[] asciiData = "id,name,role\n1,admin,SYSTEM\n2,user,NORMAL".getBytes(StandardCharsets.US_ASCII);
        EncodingConfidence result = CsvEncodingSniffer.detect(asciiData);
        assertEquals(StandardCharsets.US_ASCII, result.charset());
        assertEquals(1.0, result.confidence());
        assertFalse(result.hasBom());
    }

    @Test
    @DisplayName("Should detect UTF-8 multi-byte Korean text without BOM")
    void testUtf8WithoutBom() {
        String csv = "번호,이름,부서\n1,홍길동,개발팀\n2,이순신,인사팀";
        byte[] utf8Data = csv.getBytes(StandardCharsets.UTF_8);

        EncodingConfidence result = CsvEncodingSniffer.detect(utf8Data);
        assertEquals(StandardCharsets.UTF_8, result.charset());
        assertEquals(0.99, result.confidence());
        assertFalse(result.hasBom());
    }

    @Test
    @DisplayName("Should detect Korean EUC-KR text without BOM")
    void testEucKr() {
        String csv = "번호,이름,주소\n1,홍길동,서울특별시";
        byte[] euckrData = csv.getBytes(CsvEncodingSniffer.CHARSET_EUC_KR);

        EncodingConfidence result = CsvEncodingSniffer.detect(euckrData);
        assertTrue(result.charset().equals(CsvEncodingSniffer.CHARSET_EUC_KR)
                || result.charset().equals(CsvEncodingSniffer.CHARSET_MS949));
        assertEquals(0.95, result.confidence());
        assertFalse(result.hasBom());
    }

    @Test
    @DisplayName("Should detect CP949 text with extended Korean syllables")
    void testCp949Expanded() {
        // '똠방각하', '쀼' are outside standard KS X 1001 (EUC-KR) and only exist in CP949 (MS949)
        String csv = "항목,설명\n1,똠방각하\n2,쀼";
        byte[] cp949Data = csv.getBytes(CsvEncodingSniffer.CHARSET_MS949);

        EncodingConfidence result = CsvEncodingSniffer.detect(cp949Data);
        assertEquals(CsvEncodingSniffer.CHARSET_MS949, result.charset());
        assertEquals(0.95, result.confidence());
        assertFalse(result.hasBom());
    }

    @Test
    @DisplayName("Should read and detect charset from InputStream")
    void testInputStreamDetection() throws IOException {
        String csv = "id,name,comment\n1,홍길동,테스트데이터";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            Charset detected = CsvEncodingSniffer.detectCharset(bais, 1024);
            assertEquals(StandardCharsets.UTF_8, detected);
        }
    }

    @Test
    @DisplayName("Should handle empty and null byte arrays safely")
    void testEmptyAndNull() {
        EncodingConfidence nullResult = CsvEncodingSniffer.detect(null);
        assertEquals(StandardCharsets.UTF_8, nullResult.charset());
        assertFalse(nullResult.hasBom());

        EncodingConfidence emptyResult = CsvEncodingSniffer.detect(new byte[0]);
        assertEquals(StandardCharsets.UTF_8, emptyResult.charset());
    }
}
