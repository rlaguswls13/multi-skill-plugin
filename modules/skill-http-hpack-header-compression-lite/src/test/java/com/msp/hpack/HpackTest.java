package com.msp.hpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HpackTest {

    @Test
    @DisplayName("RFC 7541 Static Table size and lookups")
    void testStaticTable() {
        assertEquals(61, HpackStaticTable.size());

        HeaderField methodGet = HpackStaticTable.get(2);
        assertEquals(":method", methodGet.name());
        assertEquals("GET", methodGet.value());

        HeaderField pathRoot = HpackStaticTable.get(4);
        assertEquals(":path", pathRoot.name());
        assertEquals("/", pathRoot.value());

        assertEquals(2, HpackStaticTable.findExact(":method", "GET"));
        assertEquals(3, HpackStaticTable.findExact(":method", "POST"));
        assertEquals(6, HpackStaticTable.findExact(":scheme", "http"));
        assertEquals(7, HpackStaticTable.findExact(":scheme", "https"));

        assertEquals(2, HpackStaticTable.findName(":method"));
        assertEquals(1, HpackStaticTable.findName(":authority"));

        assertThrows(IndexOutOfBoundsException.class, () -> HpackStaticTable.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> HpackStaticTable.get(62));
    }

    @Test
    @DisplayName("Integer representation encoding and decoding")
    void testIntegerEncodingDecoding() throws IOException {
        // Example 1: 10 with 5-bit prefix
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HpackEncoder.encodeInt(out, 0x00, 5, 10);
        byte[] bytes = out.toByteArray();
        assertEquals(1, bytes.length);
        assertEquals(10, bytes[0]);

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        int decoded = HpackDecoder.decodeInt(in, in.read(), 5);
        assertEquals(10, decoded);

        // Example 2: 1337 with 5-bit prefix (RFC 7541 Section 5.1.2)
        out = new ByteArrayOutputStream();
        HpackEncoder.encodeInt(out, 0x00, 5, 1337);
        bytes = out.toByteArray();
        assertEquals(3, bytes.length);
        assertEquals(31, bytes[0] & 0x1F); // prefix filled

        in = new ByteArrayInputStream(bytes);
        decoded = HpackDecoder.decodeInt(in, in.read(), 5);
        assertEquals(1337, decoded);
    }

    @Test
    @DisplayName("RFC 7541 Appendix C.4.1 Huffman coding verification")
    void testHuffmanExample() {
        String input = "www.example.com";
        byte[] encoded = HpackHuffman.encode(input);

        // RFC 7541 C.4.1 expected bytes: f1 e3 c2 e5 f2 3a 6b a0 ab 90 f4 ff
        byte[] expected = new byte[] {
                (byte) 0xf1, (byte) 0xe3, (byte) 0xc2, (byte) 0xe5,
                (byte) 0xf2, (byte) 0x3a, (byte) 0x6b, (byte) 0xa0,
                (byte) 0xab, (byte) 0x90, (byte) 0xf4, (byte) 0xff
        };
        assertArrayEquals(expected, encoded);

        String decoded = HpackHuffman.decodeToString(encoded);
        assertEquals(input, decoded);
    }

    @Test
    @DisplayName("Dynamic Table eviction based on byte capacity")
    void testDynamicTableEviction() {
        // Entry size: name.length + value.length + 32
        // "custom": 6, "val1": 4 -> 6 + 4 + 32 = 42 bytes
        HpackDynamicTable table = new HpackDynamicTable(100);

        HeaderField h1 = new HeaderField("custom-1", "val1"); // 8 + 4 + 32 = 44 bytes
        HeaderField h2 = new HeaderField("custom-2", "val2"); // 8 + 4 + 32 = 44 bytes
        HeaderField h3 = new HeaderField("custom-3", "val3"); // 8 + 4 + 32 = 44 bytes

        table.add(h1);
        assertEquals(1, table.length());
        assertEquals(44, table.getCurrentSize());

        table.add(h2);
        assertEquals(2, table.length());
        assertEquals(88, table.getCurrentSize());

        // Adding h3 will exceed 100 bytes (88 + 44 = 132 > 100), evicting h1
        table.add(h3);
        assertEquals(2, table.length());
        assertEquals(88, table.getCurrentSize());
        assertEquals("custom-3", table.getRelative(0).name());
        assertEquals("custom-2", table.getRelative(1).name());

        // Absolute index lookup
        assertEquals("custom-3", table.get(62).name());
        assertEquals("custom-2", table.get(63).name());
    }

    @Test
    @DisplayName("HPACK End-to-End encode and decode with stateful dynamic table")
    void testEndToEndCompression() {
        HpackEncoder encoder = new HpackEncoder(4096, true);
        HpackDecoder decoder = new HpackDecoder(4096);

        List<HeaderField> request1 = List.of(
                new HeaderField(":method", "GET"),
                new HeaderField(":scheme", "https"),
                new HeaderField(":path", "/api/v1/users"),
                new HeaderField(":authority", "api.example.com"),
                new HeaderField("custom-header", "custom-value-12345"),
                new HeaderField("authorization", "Bearer secret-token", true)
        );

        byte[] encoded1 = encoder.encode(request1);
        List<HeaderField> decoded1 = decoder.decode(encoded1);

        assertEquals(request1.size(), decoded1.size());
        for (int i = 0; i < request1.size(); i++) {
            assertEquals(request1.get(i).name(), decoded1.get(i).name());
            assertEquals(request1.get(i).value(), decoded1.get(i).value());
        }

        // Second request reusing the indexed headers
        List<HeaderField> request2 = List.of(
                new HeaderField(":method", "GET"),
                new HeaderField(":scheme", "https"),
                new HeaderField(":path", "/api/v1/users"),
                new HeaderField(":authority", "api.example.com"),
                new HeaderField("custom-header", "custom-value-12345")
        );

        byte[] encoded2 = encoder.encode(request2);
        // Second block should be significantly smaller because headers are indexed in dynamic table
        assertTrue(encoded2.length < encoded1.length);

        List<HeaderField> decoded2 = decoder.decode(encoded2);
        assertEquals(request2.size(), decoded2.size());
        for (int i = 0; i < request2.size(); i++) {
            assertEquals(request2.get(i).name(), decoded2.get(i).name());
            assertEquals(request2.get(i).value(), decoded2.get(i).value());
        }
    }

    @Test
    @DisplayName("Dynamic Table size update handling")
    void testDynamicTableSizeUpdate() {
        HpackEncoder encoder = new HpackEncoder(4096, true);
        HpackDecoder decoder = new HpackDecoder(4096);

        byte[] updateBytes = encoder.encodeTableSizeUpdate(2048);
        decoder.decode(updateBytes);

        assertEquals(2048, encoder.getDynamicTable().getMaxCapacity());
        assertEquals(2048, decoder.getDynamicTable().getMaxCapacity());
    }

    @Test
    @DisplayName("Error handling on invalid or truncated HPACK input")
    void testErrorHandling() {
        HpackDecoder decoder = new HpackDecoder();

        // Indexed header with index 0 is invalid
        byte[] invalidIndexedZero = new byte[] { (byte) 0x80 };
        assertThrows(HpackException.class, () -> decoder.decode(invalidIndexedZero));

        // Truncated string literal
        byte[] truncatedLiteral = new byte[] { 0x40, 0x01, 0x61, 0x05, 0x61 }; // expected 5 bytes, got 1
        assertThrows(HpackException.class, () -> decoder.decode(truncatedLiteral));
    }
}
