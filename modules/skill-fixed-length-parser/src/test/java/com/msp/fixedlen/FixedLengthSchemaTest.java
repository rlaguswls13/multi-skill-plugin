package com.msp.fixedlen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FixedLengthSchemaTest {

    @Test
    @DisplayName("Financial transaction record packing and unpacking")
    void testFinancialRecordPackUnpack() {
        FixedLengthSchema schema = FixedLengthSchema.builder()
                .addField("txId", 10, Alignment.LEFT, ' ')
                .addField("amount", 12, Alignment.RIGHT, '0')
                .addField("currency", 3, Alignment.LEFT, ' ')
                .addField("status", 2, Alignment.LEFT, ' ')
                .build();

        assertEquals(27, schema.getTotalByteLength());

        Map<String, Object> input = Map.of(
                "txId", "TX98765",
                "amount", 150000,
                "currency", "KRW",
                "status", "OK"
        );

        String packed = schema.pack(input);
        assertEquals(27, packed.getBytes(StandardCharsets.UTF_8).length);
        assertEquals("TX98765   000000150000KRWOK", packed);

        FixedLengthRecord record = schema.unpack(packed);
        assertEquals("TX98765", record.getString("txId"));
        assertEquals(150000, record.getInt("amount"));
        assertEquals(150000L, record.getLong("amount"));
        assertEquals(150000.0, record.getDouble("amount"));
        assertEquals("KRW", record.getString("currency"));
        assertEquals("OK", record.getString("status"));
        assertEquals("000000150000", record.getRaw("amount"));
    }

    @Test
    @DisplayName("Zero values in right-aligned zero-padded field")
    void testZeroValueHandling() {
        FixedLengthSchema schema = FixedLengthSchema.builder()
                .addField("fee", 6, Alignment.RIGHT, '0')
                .build();

        String packed = schema.pack(Map.of("fee", 0));
        assertEquals("000000", packed);

        FixedLengthRecord record = schema.unpack(packed);
        assertEquals("0", record.getString("fee"));
        assertEquals(0, record.getInt("fee"));
    }

    @Test
    @DisplayName("Safe character-boundary truncation with UTF-8 multi-byte characters")
    void testUtf8MultiByteTruncation() {
        // "홍길동" in UTF-8 is 9 bytes (3 bytes per Hangul syllable).
        // If byte length is 7: "홍" (3B) + "길" (3B) = 6B, next syllable "동" is 3B (total 9B > 7B).
        // So safe truncation must yield "홍길" (6 bytes) + 1 space padding = 7 bytes total.
        FixedLengthSchema schema = FixedLengthSchema.builder()
                .charset(StandardCharsets.UTF_8)
                .addField("koreanName", 7, Alignment.LEFT, ' ')
                .build();

        String packed = schema.pack(Map.of("koreanName", "홍길동"));
        byte[] bytes = packed.getBytes(StandardCharsets.UTF_8);
        assertEquals(7, bytes.length);

        FixedLengthRecord record = schema.unpack(packed);
        assertEquals("홍길", record.getString("koreanName"));
    }

    @Test
    @DisplayName("EUC-KR character boundary validation and round-trip")
    void testEucKrCharsetSupport() {
        Charset eucKr = Charset.forName("EUC-KR");
        // "가나다라" in EUC-KR is 8 bytes (2 bytes per Hangul syllable).
        FixedLengthSchema schema = FixedLengthSchema.builder()
                .charset(eucKr)
                .addField("code", 4, Alignment.LEFT, ' ')
                .addField("name", 8, Alignment.LEFT, ' ')
                .addField("seq", 5, Alignment.RIGHT, '0')
                .build();

        Map<String, Object> input = Map.of(
                "code", "A1",
                "name", "홍길동",
                "seq", 42
        );

        byte[] packedBytes = schema.packToBytes(input);
        assertEquals(17, packedBytes.length);

        FixedLengthRecord record = schema.unpack(packedBytes);
        assertEquals("A1", record.getString("code"));
        assertEquals("홍길동", record.getString("name"));
        assertEquals(42, record.getInt("seq"));
    }

    @Test
    @DisplayName("Strict overflow without truncation throws FixedLengthException")
    void testStrictOverflowException() {
        FixedLengthSchema schema = FixedLengthSchema.builder()
                .addField("code", 5, Alignment.LEFT, ' ', false)
                .build();

        assertThrows(FixedLengthException.class, () -> schema.pack(Map.of("code", "EXCEED_LIMIT")));
    }

    @Test
    @DisplayName("Unpack validation with insufficient input bytes")
    void testUnderflowUnpack() {
        FixedLengthSchema schema = FixedLengthSchema.builder()
                .addField("txId", 10)
                .addField("amount", 5)
                .build();

        assertThrows(FixedLengthException.class, () -> schema.unpack("SHORT"));
    }

    @Test
    @DisplayName("Missing field accessor exception")
    void testMissingField() {
        FixedLengthSchema schema = FixedLengthSchema.builder()
                .addField("col1", 5)
                .build();

        FixedLengthRecord record = schema.unpack("12345");
        assertThrows(FixedLengthException.class, () -> record.getString("unknown"));
    }
}
