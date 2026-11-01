package com.msp.byteunit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ByteUnitFormatter Unit Tests")
class ByteUnitFormatterTest {

    @Test
    @DisplayName("Format zero bytes returns '0 B'")
    void testFormatZeroBytes() {
        assertEquals("0 B", ByteUnitFormatter.format(0));
        assertEquals("0 B", ByteUnitFormatter.format(0, ByteUnitStandard.IEC, 3));
        assertEquals("0 B", ByteUnitFormatter.format(0, ByteUnitStandard.SI, 1));
    }

    @Test
    @DisplayName("Format IEC standard across unit ranges (B to PiB)")
    void testFormatIECRanges() {
        assertEquals("512 B", ByteUnitFormatter.format(512L, ByteUnitStandard.IEC, 2));
        assertEquals("1.00 KiB", ByteUnitFormatter.format(1024L, ByteUnitStandard.IEC, 2));
        assertEquals("1.50 MiB", ByteUnitFormatter.format(1572864L, ByteUnitStandard.IEC, 2));
        assertEquals("2.00 GiB", ByteUnitFormatter.format(2L * 1024 * 1024 * 1024, ByteUnitStandard.IEC, 2));
        assertEquals("4.00 TiB", ByteUnitFormatter.format(4L * 1024 * 1024 * 1024 * 1024, ByteUnitStandard.IEC, 2));
        assertEquals("8.00 PiB", ByteUnitFormatter.format(8L * 1024 * 1024 * 1024 * 1024 * 1024, ByteUnitStandard.IEC, 2));
    }

    @Test
    @DisplayName("Format SI standard across unit ranges (B to PB)")
    void testFormatSIRanges() {
        assertEquals("500 B", ByteUnitFormatter.format(500L, ByteUnitStandard.SI, 2));
        assertEquals("1.00 KB", ByteUnitFormatter.format(1000L, ByteUnitStandard.SI, 2));
        assertEquals("1.50 MB", ByteUnitFormatter.format(1_500_000L, ByteUnitStandard.SI, 2));
        assertEquals("2.00 GB", ByteUnitFormatter.format(2_000_000_000L, ByteUnitStandard.SI, 2));
        assertEquals("3.00 TB", ByteUnitFormatter.format(3_000_000_000_000L, ByteUnitStandard.SI, 2));
        assertEquals("5.00 PB", ByteUnitFormatter.format(5_000_000_000_000_000L, ByteUnitStandard.SI, 2));
    }

    @Test
    @DisplayName("Format with custom decimal places (0, 1, 3)")
    void testFormatDecimalPlaces() {
        long bytes = 1572864L; // 1.5 MiB
        assertEquals("2 MiB", ByteUnitFormatter.format(bytes, ByteUnitStandard.IEC, 0));
        assertEquals("1.5 MiB", ByteUnitFormatter.format(bytes, ByteUnitStandard.IEC, 1));
        assertEquals("1.500 MiB", ByteUnitFormatter.format(bytes, ByteUnitStandard.IEC, 3));
    }

    @Test
    @DisplayName("Format negative byte counts")
    void testFormatNegativeBytes() {
        assertEquals("-500 B", ByteUnitFormatter.format(-500L, ByteUnitStandard.IEC, 2));
        assertEquals("-1.50 MiB", ByteUnitFormatter.format(-1572864L, ByteUnitStandard.IEC, 2));
        assertEquals("-10.00 GB", ByteUnitFormatter.format(-10_000_000_000L, ByteUnitStandard.SI, 2));
    }

    @Test
    @DisplayName("Format boundary values Long.MAX_VALUE and Long.MIN_VALUE")
    void testFormatBoundaryValues() {
        assertDoesNotThrow(() -> {
            String maxIec = ByteUnitFormatter.format(Long.MAX_VALUE, ByteUnitStandard.IEC, 2);
            assertTrue(maxIec.endsWith("EiB"));

            String minIec = ByteUnitFormatter.format(Long.MIN_VALUE, ByteUnitStandard.IEC, 2);
            assertTrue(minIec.startsWith("-") && minIec.endsWith("EiB"));
        });
    }

    @Test
    @DisplayName("Parse various human-readable byte formats with whitespace tolerance")
    void testParseValidInputs() {
        assertEquals(1610612736L, ByteUnitFormatter.parse("1.5 GiB"));
        assertEquals(1610612736L, ByteUnitFormatter.parse("1.5GiB"));
        assertEquals(500000000L, ByteUnitFormatter.parse("500MB"));
        assertEquals(500000000L, ByteUnitFormatter.parse(" 500 MB "));
        assertEquals(1024L, ByteUnitFormatter.parse("1024 B"));
        assertEquals(1024L, ByteUnitFormatter.parse("1024b"));
        assertEquals(2355L, ByteUnitFormatter.parse("2.3KiB"));
        assertEquals(0L, ByteUnitFormatter.parse("0 B"));
        assertEquals(0L, ByteUnitFormatter.parse("0"));
        assertEquals(-1048576L, ByteUnitFormatter.parse("-1 MiB"));
    }

    @Test
    @DisplayName("Bidirectional parse and format consistency")
    void testBidirectionalRoundTrip() {
        long original = 104857600L; // 100 MiB
        String formatted = ByteUnitFormatter.format(original); // "100.00 MiB"
        long parsed = ByteUnitFormatter.parse(formatted);
        assertEquals(original, parsed);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Parse throws on null or blank inputs")
    void testParseNullOrBlank(String input) {
        assertThrows(IllegalArgumentException.class, () -> ByteUnitFormatter.parse(input));
    }

    @Test
    @DisplayName("Parse throws on invalid format or unknown units")
    void testParseInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> ByteUnitFormatter.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> ByteUnitFormatter.parse("100 XYZ"));
        assertThrows(IllegalArgumentException.class, () -> ByteUnitFormatter.parse("1.2.3 MiB"));
    }

    @Test
    @DisplayName("Parse throws on 64-bit long capacity overflow")
    void testParseOverflow() {
        assertThrows(ArithmeticException.class, () -> ByteUnitFormatter.parse("999999999999999999999999 GiB"));
    }

    @Test
    @DisplayName("Format throws on negative decimal places")
    void testFormatNegativeDecimals() {
        assertThrows(IllegalArgumentException.class, () ->
                ByteUnitFormatter.format(1024, ByteUnitStandard.IEC, -1));
    }
}
