package com.msp.bizday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BusinessDayCalculator Tests")
class BusinessDayCalculatorTest {

    private final BusinessDayCalculator standardCalc = BusinessDayCalculator.standard();

    @Test
    @DisplayName("isBusinessDay correctly identifies weekends and weekdays")
    void testStandardBusinessDays() {
        LocalDate friday = LocalDate.of(2024, 5, 3);
        LocalDate saturday = LocalDate.of(2024, 5, 4);
        LocalDate sunday = LocalDate.of(2024, 5, 5);
        LocalDate monday = LocalDate.of(2024, 5, 6);

        assertTrue(standardCalc.isBusinessDay(friday));
        assertFalse(standardCalc.isBusinessDay(saturday));
        assertFalse(standardCalc.isBusinessDay(sunday));
        assertTrue(standardCalc.isBusinessDay(monday));
    }

    @Test
    @DisplayName("Calculator with custom holiday set")
    void testCustomHolidaySet() {
        LocalDate holidayMonday = LocalDate.of(2024, 5, 6);
        BusinessDayCalculator calc = BusinessDayCalculator.of(Set.of(holidayMonday));

        assertFalse(calc.isBusinessDay(holidayMonday));
        assertTrue(calc.isBusinessDay(holidayMonday.plusDays(1))); // Tuesday
    }

    @Test
    @DisplayName("nextBusinessDay skips weekends and holidays")
    void testNextBusinessDay() {
        LocalDate friday = LocalDate.of(2024, 5, 3);
        LocalDate monday = LocalDate.of(2024, 5, 6);
        LocalDate tuesday = LocalDate.of(2024, 5, 7);

        assertEquals(monday, standardCalc.nextBusinessDay(friday));

        // When Monday is a holiday
        BusinessDayCalculator calc = BusinessDayCalculator.of(Set.of(monday));
        assertEquals(tuesday, calc.nextBusinessDay(friday));
    }

    @Test
    @DisplayName("previousBusinessDay skips weekends and holidays")
    void testPreviousBusinessDay() {
        LocalDate monday = LocalDate.of(2024, 5, 6);
        LocalDate friday = LocalDate.of(2024, 5, 3);
        LocalDate thursday = LocalDate.of(2024, 5, 2);

        assertEquals(friday, standardCalc.previousBusinessDay(monday));

        // When Friday is a holiday
        BusinessDayCalculator calc = BusinessDayCalculator.of(Set.of(friday));
        assertEquals(thursday, calc.previousBusinessDay(monday));
    }

    @Test
    @DisplayName("addBusinessDays with positive, negative, and zero values")
    void testAddBusinessDays() {
        LocalDate wednesday = LocalDate.of(2024, 5, 1);

        // Wed + 2 -> Fri
        assertEquals(LocalDate.of(2024, 5, 3), standardCalc.addBusinessDays(wednesday, 2));

        // Wed + 3 -> Mon (skips Sat & Sun)
        assertEquals(LocalDate.of(2024, 5, 6), standardCalc.addBusinessDays(wednesday, 3));

        // Mon - 1 -> Fri
        LocalDate monday = LocalDate.of(2024, 5, 6);
        assertEquals(LocalDate.of(2024, 5, 3), standardCalc.addBusinessDays(monday, -1));

        // Mon - 3 -> Wed
        assertEquals(LocalDate.of(2024, 5, 1), standardCalc.addBusinessDays(monday, -3));

        // Zero days on business day returns same
        assertEquals(wednesday, standardCalc.addBusinessDays(wednesday, 0));

        // Zero days on Saturday moves to next Monday
        LocalDate saturday = LocalDate.of(2024, 5, 4);
        assertEquals(monday, standardCalc.addBusinessDays(saturday, 0));
    }

    @Test
    @DisplayName("countBusinessDays half-open interval [start, end)")
    void testCountBusinessDays() {
        LocalDate mon = LocalDate.of(2024, 5, 6);
        LocalDate fri = LocalDate.of(2024, 5, 10);
        LocalDate nextMon = LocalDate.of(2024, 5, 13);

        assertEquals(4, standardCalc.countBusinessDays(mon, fri)); // Mon, Tue, Wed, Thu
        assertEquals(5, standardCalc.countBusinessDays(mon, nextMon)); // Mon-Fri
        assertEquals(0, standardCalc.countBusinessDays(mon, mon)); // Same date

        // Reverse interval produces negative count
        assertEquals(-5, standardCalc.countBusinessDays(nextMon, mon));
    }

    @Test
    @DisplayName("countBusinessDaysInclusive closed interval [start, end]")
    void testCountBusinessDaysInclusive() {
        LocalDate mon = LocalDate.of(2024, 5, 6);
        LocalDate fri = LocalDate.of(2024, 5, 10);

        assertEquals(5, standardCalc.countBusinessDaysInclusive(mon, fri)); // Mon, Tue, Wed, Thu, Fri
    }

    @Test
    @DisplayName("Custom weekend definition (e.g. Friday and Saturday)")
    void testCustomWeekend() {
        BusinessDayCalculator middleEastCalc = BusinessDayCalculator.builder()
                .weekend(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
                .build();

        LocalDate thursday = LocalDate.of(2024, 5, 2);
        LocalDate friday = LocalDate.of(2024, 5, 3);
        LocalDate saturday = LocalDate.of(2024, 5, 4);
        LocalDate sunday = LocalDate.of(2024, 5, 5);

        assertTrue(middleEastCalc.isBusinessDay(thursday));
        assertFalse(middleEastCalc.isBusinessDay(friday));
        assertFalse(middleEastCalc.isBusinessDay(saturday));
        assertTrue(middleEastCalc.isBusinessDay(sunday)); // Sunday is a working day!
    }
}
