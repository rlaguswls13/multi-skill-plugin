package com.msp.msgpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessagePackCodecTest {

    @Test
    @DisplayName("Null, boolean true, boolean false roundtrip")
    void testNullAndBooleans() {
        assertNull(MessagePackCodec.decode(MessagePackCodec.encode(null)));
        assertEquals(Boolean.TRUE, MessagePackCodec.decode(MessagePackCodec.encode(true)));
        assertEquals(Boolean.FALSE, MessagePackCodec.decode(MessagePackCodec.encode(false)));
    }

    @Test
    @DisplayName("Positive fixint and unsigned integers roundtrip")
    void testPositiveIntegers() {
        // Fixint (0 - 127)
        assertEquals(0L, MessagePackCodec.decode(MessagePackCodec.encode(0)));
        assertEquals(127L, MessagePackCodec.decode(MessagePackCodec.encode(127)));

        // uint 8 (128 - 255)
        assertEquals(128L, MessagePackCodec.decode(MessagePackCodec.encode(128)));
        assertEquals(255L, MessagePackCodec.decode(MessagePackCodec.encode(255)));

        // uint 16 (256 - 65535)
        assertEquals(1000L, MessagePackCodec.decode(MessagePackCodec.encode(1000)));
        assertEquals(65535L, MessagePackCodec.decode(MessagePackCodec.encode(65535)));

        // uint 32
        assertEquals(100_000L, MessagePackCodec.decode(MessagePackCodec.encode(100_000L)));

        // uint 64 / large long
        long big = 5_000_000_000L;
        assertEquals(big, MessagePackCodec.decode(MessagePackCodec.encode(big)));
    }

    @Test
    @DisplayName("Negative fixint and signed integers roundtrip")
    void testNegativeIntegers() {
        // Negative fixint (-32 to -1)
        assertEquals(-1L, MessagePackCodec.decode(MessagePackCodec.encode(-1)));
        assertEquals(-32L, MessagePackCodec.decode(MessagePackCodec.encode(-32)));

        // int 8 (-128 to -33)
        assertEquals(-33L, MessagePackCodec.decode(MessagePackCodec.encode(-33)));
        assertEquals(-128L, MessagePackCodec.decode(MessagePackCodec.encode(-128)));

        // int 16 (-32768 to -129)
        assertEquals(-500L, MessagePackCodec.decode(MessagePackCodec.encode(-500)));
        assertEquals(-32768L, MessagePackCodec.decode(MessagePackCodec.encode(-32768)));

        // int 32
        assertEquals(-100_000L, MessagePackCodec.decode(MessagePackCodec.encode(-100_000)));

        // int 64
        long bigNeg = -5_000_000_000L;
        assertEquals(bigNeg, MessagePackCodec.decode(MessagePackCodec.encode(bigNeg)));
    }

    @Test
    @DisplayName("Floating point numbers roundtrip")
    void testFloatsAndDoubles() {
        float f = 3.14159f;
        Float decodedFloat = (Float) MessagePackCodec.decode(MessagePackCodec.encode(f));
        assertEquals(f, decodedFloat, 0.0001f);

        double d = 2.718281828459045;
        Double decodedDouble = (Double) MessagePackCodec.decode(MessagePackCodec.encode(d));
        assertEquals(d, decodedDouble, 0.0000000001);
    }

    @Test
    @DisplayName("Strings: fixstr, str8, unicode UTF-8")
    void testStrings() {
        // Empty
        assertEquals("", MessagePackCodec.decode(MessagePackCodec.encode("")));

        // Short fixstr (< 32 chars)
        assertEquals("hello world", MessagePackCodec.decode(MessagePackCodec.encode("hello world")));

        // str8 (> 31 chars)
        String medium = "This is a longer test string to exercise str8 format in MessagePack!";
        assertEquals(medium, MessagePackCodec.decode(MessagePackCodec.encode(medium)));

        // UTF-8 Unicode
        String unicode = "안녕하세요! 지구와 달 🚀✨";
        assertEquals(unicode, MessagePackCodec.decode(MessagePackCodec.encode(unicode)));
    }

    @Test
    @DisplayName("Binary byte[] roundtrip")
    void testBinary() {
        byte[] expected = new byte[]{0x00, (byte) 0xff, 0x42, (byte) 0xca, (byte) 0xfe};
        byte[] encoded = MessagePackCodec.encode(expected);
        byte[] decoded = (byte[]) MessagePackCodec.decode(encoded);

        assertArrayEquals(expected, decoded);
    }

    @Test
    @DisplayName("List and Map collections roundtrip")
    void testCollections() {
        List<Object> list = List.of("item1", 42L, true, 3.14f);
        byte[] listEncoded = MessagePackCodec.encode(list);
        List<Object> listDecoded = MessagePackCodec.decodeList(listEncoded);

        assertEquals(4, listDecoded.size());
        assertEquals("item1", listDecoded.get(0));
        assertEquals(42L, listDecoded.get(1));
        assertEquals(true, listDecoded.get(2));

        Map<String, Object> map = Map.of(
            "name", "Alice",
            "age", 30L,
            "active", true
        );
        byte[] mapEncoded = MessagePackCodec.encode(map);
        Map<String, Object> mapDecoded = MessagePackCodec.decodeMap(mapEncoded);

        assertEquals("Alice", mapDecoded.get("name"));
        assertEquals(30L, mapDecoded.get("age"));
        assertEquals(true, mapDecoded.get("active"));
    }

    @Test
    @DisplayName("Nested structure with maps and lists")
    void testNestedStructure() {
        Map<String, Object> payload = Map.of(
            "status", "OK",
            "code", 200L,
            "data", Map.of(
                "items", List.of("alpha", "beta", "gamma"),
                "total", 3L
            )
        );

        byte[] encoded = MessagePackCodec.encode(payload);
        Map<String, Object> decoded = MessagePackCodec.decodeMap(encoded);

        assertEquals("OK", decoded.get("status"));
        assertEquals(200L, decoded.get("code"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) decoded.get("data");
        assertNotNull(data);
        assertEquals(3L, data.get("total"));

        @SuppressWarnings("unchecked")
        List<String> items = (List<String>) data.get("items");
        assertEquals(List.of("alpha", "beta", "gamma"), items);
    }
}
