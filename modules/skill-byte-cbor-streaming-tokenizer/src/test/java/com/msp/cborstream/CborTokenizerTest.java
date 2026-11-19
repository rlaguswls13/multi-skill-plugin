package com.msp.cborstream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CborTokenizerTest {

    @Test
    @DisplayName("Encode and decode unsigned and negative integers")
    void testIntegers() throws IOException {
        CborWriter writer = CborWriter.create();
        writer.writeInt(0);
        writer.writeInt(23);
        writer.writeInt(24);
        writer.writeInt(255);
        writer.writeInt(1000);
        writer.writeInt(1000000L);
        writer.writeInt(-1);
        writer.writeInt(-10);
        writer.writeInt(-500);

        byte[] cbor = writer.toByteArray();
        try (CborTokenizer tokenizer = CborTokenizer.fromBytes(cbor)) {
            assertEquals(0, tokenizer.nextToken().longValue());
            assertEquals(23, tokenizer.nextToken().longValue());
            assertEquals(24, tokenizer.nextToken().longValue());
            assertEquals(255, tokenizer.nextToken().longValue());
            assertEquals(1000, tokenizer.nextToken().longValue());
            assertEquals(1000000L, tokenizer.nextToken().longValue());
            assertEquals(-1, tokenizer.nextToken().longValue());
            assertEquals(-10, tokenizer.nextToken().longValue());
            assertEquals(-500, tokenizer.nextToken().longValue());
            assertEquals(CborTokenType.END_OF_STREAM, tokenizer.nextToken().type());
        }
    }

    @Test
    @DisplayName("Encode and decode text strings and byte strings")
    void testStringsAndBytes() throws IOException {
        CborWriter writer = CborWriter.create();
        writer.writeTextString("Hello CBOR");
        writer.writeByteString(new byte[]{0x01, 0x02, (byte) 0xFE, (byte) 0xFF});

        byte[] cbor = writer.toByteArray();
        try (CborTokenizer tokenizer = CborTokenizer.fromBytes(cbor)) {
            CborToken text = tokenizer.nextToken();
            assertEquals(CborTokenType.TEXT_STRING, text.type());
            assertEquals("Hello CBOR", text.textValue());

            CborToken bytes = tokenizer.nextToken();
            assertEquals(CborTokenType.BYTE_STRING, bytes.type());
            assertArrayEquals(new byte[]{0x01, 0x02, (byte) 0xFE, (byte) 0xFF}, bytes.bytesValue());
        }
    }

    @Test
    @DisplayName("Encode and decode nested arrays and maps")
    void testArraysAndMaps() throws IOException {
        CborWriter writer = CborWriter.create();
        // Array of 3 elements: [1, "two", [3]]
        writer.writeStartArray(3);
        writer.writeInt(1);
        writer.writeTextString("two");
        writer.writeStartArray(1);
        writer.writeInt(3);

        byte[] cbor = writer.toByteArray();
        try (CborTokenizer tokenizer = CborTokenizer.fromBytes(cbor)) {
            CborToken a1 = tokenizer.nextToken();
            assertEquals(CborTokenType.START_ARRAY, a1.type());
            assertEquals(3, a1.length());
            assertFalse(a1.isIndefinite());

            assertEquals(1, tokenizer.nextToken().longValue());
            assertEquals("two", tokenizer.nextToken().textValue());

            CborToken a2 = tokenizer.nextToken();
            assertEquals(CborTokenType.START_ARRAY, a2.type());
            assertEquals(1, a2.length());
            assertEquals(3, tokenizer.nextToken().longValue());
        }
    }

    @Test
    @DisplayName("Encode and decode indefinite length array and map with break")
    void testIndefiniteLength() throws IOException {
        CborWriter writer = CborWriter.create();
        writer.writeStartIndefiniteArray();
        writer.writeInt(10);
        writer.writeInt(20);
        writer.writeBreak();

        byte[] cbor = writer.toByteArray();
        try (CborTokenizer tokenizer = CborTokenizer.fromBytes(cbor)) {
            CborToken start = tokenizer.nextToken();
            assertEquals(CborTokenType.START_ARRAY, start.type());
            assertTrue(start.isIndefinite());

            assertEquals(10, tokenizer.nextToken().longValue());
            assertEquals(20, tokenizer.nextToken().longValue());

            CborToken brk = tokenizer.nextToken();
            assertEquals(CborTokenType.BREAK, brk.type());
        }
    }

    @Test
    @DisplayName("Simple values: booleans, null, undefined, and floating point")
    void testSimpleValuesAndFloats() throws IOException {
        CborWriter writer = CborWriter.create();
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeNull();
        writer.writeUndefined();
        writer.writeFloat(3.14f);
        writer.writeDouble(2.7182818284);

        byte[] cbor = writer.toByteArray();
        try (CborTokenizer tokenizer = CborTokenizer.fromBytes(cbor)) {
            assertTrue(tokenizer.nextToken().booleanValue());
            assertFalse(tokenizer.nextToken().booleanValue());
            assertEquals(CborTokenType.NULL, tokenizer.nextToken().type());
            assertEquals(CborTokenType.UNDEFINED, tokenizer.nextToken().type());
            assertEquals(3.14f, (float) tokenizer.nextToken().doubleValue(), 0.0001f);
            assertEquals(2.7182818284, tokenizer.nextToken().doubleValue(), 0.000001);
        }
    }

    @Test
    @DisplayName("Tags: RFC 8949 tag decoding")
    void testTags() throws IOException {
        CborWriter writer = CborWriter.create();
        writer.writeTag(32); // Tag 32: URI
        writer.writeTextString("https://example.com");

        byte[] cbor = writer.toByteArray();
        try (CborTokenizer tokenizer = CborTokenizer.fromBytes(cbor)) {
            CborToken tag = tokenizer.nextToken();
            assertEquals(CborTokenType.TAG, tag.type());
            assertEquals(32, tag.tagValue());

            CborToken uri = tokenizer.nextToken();
            assertEquals("https://example.com", uri.textValue());
        }
    }

    @Test
    @DisplayName("Diagnostic notation formatter formats complex CBOR structure")
    void testDiagnosticFormatter() throws IOException {
        CborWriter writer = CborWriter.create();
        // Map: {1: "text", 2: [1, 2], 3: true, 4: null}
        writer.writeStartMap(4);
        writer.writeInt(1);
        writer.writeTextString("text");
        writer.writeInt(2);
        writer.writeStartArray(2);
        writer.writeInt(1);
        writer.writeInt(2);
        writer.writeInt(3);
        writer.writeBoolean(true);
        writer.writeInt(4);
        writer.writeNull();

        byte[] cbor = writer.toByteArray();
        String diagnostic = CborDiagnosticFormatter.format(cbor);

        assertEquals("{1: \"text\", 2: [1, 2], 3: true, 4: null}", diagnostic);
    }
}
