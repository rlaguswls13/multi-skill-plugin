package com.msp.hexdump;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HexDumperTest {

    @Test
    @DisplayName("Classic 3-column Wireshark layout without ANSI colors")
    void testClassicLayoutWithoutAnsi() {
        // "Hello World!\n\0\1\u00ff"
        byte[] data = new byte[]{
                0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x57, 0x6f,  // Hello Wo
                0x72, 0x6c, 0x64, 0x21, 0x0a, 0x00, 0x01, (byte) 0xff // rld!\n\0\1\ff
        };

        String dump = HexDumper.create()
                .withAnsiColors(false)
                .format(data);

        String expected = "00000000  48 65 6c 6c 6f 20 57 6f  72 6c 64 21 0a 00 01 ff  |Hello World!....|";
        assertEquals(expected, dump);
    }

    @Test
    @DisplayName("Aligns ASCII column with spaces for partial line")
    void testPartialLineAlignment() {
        byte[] data = "Short message".getBytes(StandardCharsets.UTF_8); // 13 bytes
        String dump = HexDumper.create()
                .withAnsiColors(false)
                .format(data);

        // 13 bytes: 8 bytes, double space, 5 bytes, then 3 missing bytes (each 2 spaces + 1 leading space)
        assertTrue(dump.startsWith("00000000  53 68 6f 72 74 20 6d 65  73 73 61 67 65"));
        assertTrue(dump.endsWith("|Short message|"));
    }

    @Test
    @DisplayName("Emits ANSI color escape sequences according to byte category")
    void testAnsiCategorization() {
        byte[] data = new byte[]{
                0x00,               // Null byte -> GRAY
                'A',                // Printable ASCII -> GREEN
                0x1B,               // Control char -> YELLOW
                (byte) 0x80         // High binary -> MAGENTA
        };

        String dump = HexDumper.create()
                .withAnsiColors(true)
                .format(data);

        assertTrue(dump.contains(AnsiColor.GRAY.code()));
        assertTrue(dump.contains(AnsiColor.GREEN.code()));
        assertTrue(dump.contains(AnsiColor.YELLOW.code()));
        assertTrue(dump.contains(AnsiColor.MAGENTA.code()));
        assertTrue(dump.contains(AnsiColor.RESET.code()));
    }

    @Test
    @DisplayName("Range highlight takes precedence over default category color")
    void testRangeHighlightPrecedence() {
        byte[] data = "HEADERPAYLOAD".getBytes(StandardCharsets.UTF_8);

        String dump = HexDumper.create()
                .withAnsiColors(true)
                .highlightRange(0, 6, AnsiColor.BRIGHT_RED, "Header")
                .format(data);

        assertTrue(dump.contains(AnsiColor.BRIGHT_RED.code()));
    }

    @Test
    @DisplayName("Supports uppercase hex formatting")
    void testUppercaseHex() {
        byte[] data = new byte[]{ (byte) 0xab, (byte) 0xcd, (byte) 0xef };
        String dump = HexDumper.create()
                .withAnsiColors(false)
                .withUppercaseHex(true)
                .format(data);

        assertTrue(dump.contains("AB CD EF"));
    }

    @Test
    @DisplayName("Custom bytes per line and column toggles")
    void testCustomBytesPerLineAndToggles() {
        byte[] data = new byte[]{ 1, 2, 3, 4, 5, 6, 7, 8 };

        String dumpNoOffset = HexDumper.create()
                .withAnsiColors(false)
                .withShowOffset(false)
                .format(data);
        assertFalse(dumpNoOffset.startsWith("00000000"));

        String dumpNoAscii = HexDumper.create()
                .withAnsiColors(false)
                .withShowAscii(false)
                .format(data);
        assertFalse(dumpNoAscii.contains("|"));
    }

    @Test
    @DisplayName("Handles empty and sub-array offsets safely")
    void testEdgeCases() {
        assertEquals("", HexDumper.dump(new byte[0]));
        assertEquals("", HexDumper.dump(null));

        byte[] data = new byte[]{ 0, 1, 2, 3, 4, 5 };
        String slice = HexDumper.create().withAnsiColors(false).format(data, 2, 2);
        assertTrue(slice.contains("02 03"));

        assertThrows(IndexOutOfBoundsException.class, () -> HexDumper.create().format(data, -1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> HexDumper.create().format(data, 0, 10));
    }
}
