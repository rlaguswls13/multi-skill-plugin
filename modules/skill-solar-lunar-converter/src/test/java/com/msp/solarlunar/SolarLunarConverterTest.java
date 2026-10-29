package com.msp.solarlunar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SolarLunarConverterTest {

    @Test
    @DisplayName("Base date round-trip (Lunar 1900-01-01 <-> Solar 1900-01-31)")
    void testBaseDate() {
        LocalDate baseSolar = LocalDate.of(1900, 1, 31);
        LunarDate lunar = SolarLunarConverter.toLunar(baseSolar);

        assertEquals(1900, lunar.year());
        assertEquals(1, lunar.month());
        assertEquals(1, lunar.day());
        assertFalse(lunar.isLeapMonth());

        Optional<LocalDate> solarBack = SolarLunarConverter.toSolar(lunar);
        assertTrue(solarBack.isPresent());
        assertEquals(baseSolar, solarBack.get());
    }

    @Test
    @DisplayName("Known historical Korean holidays (Seollal & Chuseok)")
    void testKnownHolidays() {
        // Seollal 2024: Lunar 2024-01-01 = Solar 2024-02-10
        LocalDate seollal2024 = LocalDate.of(2024, 2, 10);
        LunarDate lunarSeollal24 = SolarLunarConverter.toLunar(seollal2024);
        assertEquals(new LunarDate(2024, 1, 1, false), lunarSeollal24);
        assertEquals(Optional.of(seollal2024), SolarLunarConverter.toSolar(2024, 1, 1, false));

        // Chuseok 2024: Lunar 2024-08-15 = Solar 2024-09-17
        LocalDate chuseok2024 = LocalDate.of(2024, 9, 17);
        LunarDate lunarChuseok24 = SolarLunarConverter.toLunar(chuseok2024);
        assertEquals(new LunarDate(2024, 8, 15, false), lunarChuseok24);
        assertEquals(Optional.of(chuseok2024), SolarLunarConverter.toSolar(2024, 8, 15, false));

        // Seollal 2023: Lunar 2023-01-01 = Solar 2023-01-22
        LocalDate seollal2023 = LocalDate.of(2023, 1, 22);
        assertEquals(new LunarDate(2023, 1, 1, false), SolarLunarConverter.toLunar(seollal2023));

        // Chuseok 2023: Lunar 2023-08-15 = Solar 2023-09-29
        LocalDate chuseok2023 = LocalDate.of(2023, 9, 29);
        assertEquals(new LunarDate(2023, 8, 15, false), SolarLunarConverter.toLunar(chuseok2023));

        // Seollal 2026: Lunar 2026-01-01 = Solar 2026-02-17
        LocalDate seollal2026 = LocalDate.of(2026, 2, 17);
        assertEquals(new LunarDate(2026, 1, 1, false), SolarLunarConverter.toLunar(seollal2026));

        // Chuseok 2026: Lunar 2026-08-15 = Solar 2026-09-25
        LocalDate chuseok2026 = LocalDate.of(2026, 9, 25);
        assertEquals(new LunarDate(2026, 8, 15, false), SolarLunarConverter.toLunar(chuseok2026));
    }

    @Test
    @DisplayName("Leap month (윤달) calculations for Year 2023 (윤2월)")
    void testLeapMonthHandling() {
        // 2023 has leap month 2
        assertEquals(2, SolarLunarConverter.getLeapMonth(2023));
        assertEquals(29, SolarLunarConverter.getLeapMonthDays(2023));

        // Regular Month 2, Day 1: 2023-02-20
        LocalDate regMonth2 = LocalDate.of(2023, 2, 20);
        assertEquals(new LunarDate(2023, 2, 1, false), SolarLunarConverter.toLunar(regMonth2));
        assertEquals(Optional.of(regMonth2), SolarLunarConverter.toSolar(2023, 2, 1, false));

        // Leap Month 2, Day 1 (윤2월 1일): 2023-03-22
        LocalDate leapMonth2 = LocalDate.of(2023, 3, 22);
        assertEquals(new LunarDate(2023, 2, 1, true), SolarLunarConverter.toLunar(leapMonth2));
        assertEquals(Optional.of(leapMonth2), SolarLunarConverter.toSolar(2023, 2, 1, true));

        // Month 3, Day 1 (3월 1일 after leap month): 2023-04-20
        LocalDate month3 = LocalDate.of(2023, 4, 20);
        assertEquals(new LunarDate(2023, 3, 1, false), SolarLunarConverter.toLunar(month3));
        assertEquals(Optional.of(month3), SolarLunarConverter.toSolar(2023, 3, 1, false));
    }

    @Test
    @DisplayName("Invalid lunar dates return Optional.empty()")
    void testInvalidDates() {
        // 2024 has no leap month
        assertEquals(0, SolarLunarConverter.getLeapMonth(2024));
        assertTrue(SolarLunarConverter.toSolar(2024, 2, 1, true).isEmpty());

        // Month 13 is invalid
        assertTrue(SolarLunarConverter.toSolar(2024, 13, 1, false).isEmpty());

        // Day 31 is invalid (lunar months have at most 30 days)
        assertTrue(SolarLunarConverter.toSolar(2024, 1, 31, false).isEmpty());

        // Year outside supported bounds
        assertTrue(SolarLunarConverter.toSolar(1899, 1, 1, false).isEmpty());
        assertTrue(SolarLunarConverter.toSolar(2101, 1, 1, false).isEmpty());
    }

    @Test
    @DisplayName("Solar date out of bounds throws IllegalArgumentException")
    void testOutOfBoundsSolarDate() {
        assertThrows(IllegalArgumentException.class, () ->
                SolarLunarConverter.toLunar(LocalDate.of(1900, 1, 30))
        );
        assertThrows(IllegalArgumentException.class, () ->
                SolarLunarConverter.toLunar(LocalDate.of(2101, 1, 1))
        );
    }

    @Test
    @DisplayName("Comprehensive 50-year 100% round-trip integrity test (2000-2050)")
    void testComprehensiveRoundTrip() {
        LocalDate current = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.of(2050, 1, 1);

        while (current.isBefore(end)) {
            final LocalDate dateToTest = current;
            LunarDate lunar = SolarLunarConverter.toLunar(dateToTest);
            Optional<LocalDate> back = SolarLunarConverter.toSolar(lunar);

            assertTrue(back.isPresent(), () -> "Failed to convert back: " + lunar);
            assertEquals(dateToTest, back.get(), () -> "Mismatch on date " + dateToTest + ", got lunar: " + lunar);

            current = current.plusDays(1);
        }
    }

    @Test
    @DisplayName("Korean string representation of LunarDate")
    void testKoreanFormatting() {
        LunarDate reg = new LunarDate(2026, 1, 1, false);
        assertEquals("음력 2026년 1월 1일", reg.toKoreanString());
        assertEquals("2026-01-01", reg.toString());

        LunarDate leap = new LunarDate(2023, 2, 15, true);
        assertEquals("음력 2023년 윤2월 15일", leap.toKoreanString());
        assertEquals("2023-L02-15", leap.toString());
    }
}
