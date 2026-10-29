package com.msp.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitConverterTest {

    private static final double EPSILON = 1e-4;

    @Test
    @DisplayName("DataSize cross-conversions between decimal (1000) and binary (1024)")
    void testDataSizeConversions() {
        // Decimal SI: 1 MB = 1,000,000 bytes
        DataSize oneMb = DataSize.ofMegabytes(1.0);
        assertEquals(1_000_000L, oneMb.toBytes());
        assertEquals(1.0, oneMb.toMegabytes(), EPSILON);
        assertEquals(1000.0, oneMb.toKilobytes(), EPSILON);
        assertEquals(0.001, oneMb.toGigabytes(), EPSILON);

        // Binary IEC: 1 MiB = 1,048,576 bytes
        DataSize oneMib = DataSize.ofMebibytes(1.0);
        assertEquals(1_048_576L, oneMib.toBytes());
        assertEquals(1.0, oneMib.toMebibytes(), EPSILON);
        assertEquals(1024.0, oneMib.toKibibytes(), EPSILON);

        // 1 GiB = 1024 MiB
        DataSize oneGib = DataSize.ofGibibytes(1.0);
        assertEquals(1024.0, oneGib.toMebibytes(), EPSILON);
        assertEquals(1_073_741_824L, oneGib.toBytes());

        // 1 TB = 1,000,000,000,000 bytes
        DataSize oneTb = DataSize.ofTerabytes(1.0);
        assertEquals(1_000_000_000_000L, oneTb.toBytes());

        // 1 PB = 1,000,000,000,000,000 bytes
        DataSize onePb = DataSize.ofPetabytes(1.0);
        assertEquals(1_000_000_000_000_000L, onePb.toBytes());
    }

    @Test
    @DisplayName("DataSize string parsing with various units and whitespace")
    void testDataSizeParsing() {
        assertEquals(10_500_000L, DataSize.parse("10.5 MB").toBytes());
        assertEquals(524_288L, DataSize.parse("512 KiB").toBytes());
        assertEquals(1024L, DataSize.parse("1024 B").toBytes());
        assertEquals(2_000_000_000L, DataSize.parse("2GB").toBytes());

        // Case-insensitive
        assertEquals(1_048_576L, DataSize.parse("1mib").toBytes());
        assertEquals(1_048_576L, DataSize.parse("1 MIB").toBytes());

        // Binary-mode parsing (treats MB as MiB 1024 base)
        DataSize binaryParsed = DataSize.parseBinary("10.5 MB");
        assertEquals(Math.round(10.5 * 1024 * 1024), binaryParsed.toBytes());

        // Invalid inputs
        assertThrows(IllegalArgumentException.class, () -> DataSize.parse("not-a-size"));
        assertThrows(IllegalArgumentException.class, () -> DataSize.parse("100 XYZ"));
    }

    @Test
    @DisplayName("DataSize human-readable formatting")
    void testDataSizeHumanReadable() {
        assertEquals("500 B", DataSize.ofBytes(500).toHumanReadable());
        assertEquals("1 KiB", DataSize.ofBytes(1024).toHumanReadable());
        assertEquals("1 MiB", DataSize.ofBytes(1_048_576).toHumanReadable());
        assertEquals("1.50 MiB", DataSize.ofBytes(1_572_864).toHumanReadable());

        // Decimal formatting
        assertEquals("1 MB", DataSize.ofBytes(1_000_000).toHumanReadable(false));
        assertEquals("1.50 MB", DataSize.ofBytes(1_500_000).toHumanReadable(false));
    }

    @Test
    @DisplayName("TimeDuration cross-conversions")
    void testTimeDurationConversions() {
        TimeDuration oneHour = TimeDuration.ofHours(1.0);
        assertEquals(60.0, oneHour.toMinutes(), EPSILON);
        assertEquals(3600.0, oneHour.toSeconds(), EPSILON);
        assertEquals(3_600_000.0, oneHour.toMillis(), EPSILON);
        assertEquals(3_600_000_000_000L, oneHour.toNanos());

        TimeDuration halfDay = TimeDuration.ofDays(0.5);
        assertEquals(12.0, halfDay.toHours(), EPSILON);

        TimeDuration fiveHundredMs = TimeDuration.ofMillis(500);
        assertEquals(0.5, fiveHundredMs.toSeconds(), EPSILON);
        assertEquals(500_000.0, fiveHundredMs.toMicros(), EPSILON);
    }

    @Test
    @DisplayName("TimeDuration string parsing with composite tokens")
    void testTimeDurationParsing() {
        // "2h 30m" = 2.5 hours = 150 minutes = 9000 seconds
        TimeDuration d1 = TimeDuration.parse("2h 30m");
        assertEquals(9000.0, d1.toSeconds(), EPSILON);

        // "1d 4h 30s" = 86400 + 4*3600 + 30 = 100,830 seconds
        TimeDuration d2 = TimeDuration.parse("1d 4h 30s");
        assertEquals(100830.0, d2.toSeconds(), EPSILON);

        // Single unit tokens
        TimeDuration d3 = TimeDuration.parse("100ms");
        assertEquals(100.0, d3.toMillis(), EPSILON);

        TimeDuration d4 = TimeDuration.parse("1.5h");
        assertEquals(90.0, d4.toMinutes(), EPSILON);

        TimeDuration d5 = TimeDuration.parse("500ns");
        assertEquals(500L, d5.toNanos());

        // Invalid formats
        assertThrows(IllegalArgumentException.class, () -> TimeDuration.parse(""));
        assertThrows(IllegalArgumentException.class, () -> TimeDuration.parse("invalid"));
        assertThrows(IllegalArgumentException.class, () -> TimeDuration.parse("2h unknown 30m"));
    }

    @Test
    @DisplayName("TimeDuration human-readable formatting")
    void testTimeDurationHumanReadable() {
        assertEquals("0s", TimeDuration.ZERO.toHumanReadable());
        assertEquals("500ns", TimeDuration.ofNanos(500).toHumanReadable());
        assertEquals("250us", TimeDuration.ofMicros(250).toHumanReadable());
        assertEquals("450ms", TimeDuration.ofMillis(450).toHumanReadable());
        assertEquals("45s", TimeDuration.ofSeconds(45).toHumanReadable());
        assertEquals("2h 30m", TimeDuration.parse("2h 30m").toHumanReadable());
        assertEquals("1d 4h", TimeDuration.parse("1d 4h").toHumanReadable());
    }

    @Test
    @DisplayName("Arithmetic and Duration interoperability")
    void testArithmeticAndInterop() {
        DataSize s1 = DataSize.ofMegabytes(10);
        DataSize s2 = DataSize.ofMegabytes(5);
        assertEquals(15_000_000L, s1.plus(s2).toBytes());
        assertEquals(5_000_000L, s1.minus(s2).toBytes());
        assertTrue(s1.compareTo(s2) > 0);

        TimeDuration t1 = TimeDuration.ofMinutes(10);
        TimeDuration t2 = TimeDuration.ofMinutes(4);
        assertEquals(14.0, t1.plus(t2).toMinutes(), EPSILON);
        assertEquals(6.0, t1.minus(t2).toMinutes(), EPSILON);

        Duration javaDur = t1.toJavaDuration();
        assertEquals(600, javaDur.toSeconds());
        TimeDuration fromJava = TimeDuration.from(javaDur);
        assertEquals(t1, fromJava);
    }
}
