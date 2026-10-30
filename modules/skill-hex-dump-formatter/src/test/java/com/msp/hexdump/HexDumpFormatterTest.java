package com.msp.hexdump;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HexDumpFormatterTest {

    @Test
    @DisplayName("Format ASCII text bytes into Hex Dump")
    void testFormatText() {
        byte[] bytes = "Hello, World!\n".getBytes(StandardCharsets.US_ASCII);
        String dump = HexDumpFormatter.format(bytes);

        assertTrue(dump.startsWith("00000000  "));
        assertTrue(dump.contains("48 65 6C 6C 6F 2C 20 57")); // "Hello, W"
        assertTrue(dump.contains("|Hello, World!.|")); // newline displayed as dot
    }

    @Test
    @DisplayName("Format multi-line binary payload with padding")
    void testMultilinePadding() {
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            bytes[i] = (byte) i;
        }

        String dump = HexDumpFormatter.format(bytes);
        String[] lines = dump.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith("00000000  "));
        assertTrue(lines[1].startsWith("00000010  "));
    }
}
