package com.msp.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KoreanHolidayCalculator Tests")
class KoreanHolidayCalculatorTest {

    private final KoreanHolidayCalculator calculator = KoreanHolidayCalculator.getInstance();

    @Test
    @DisplayName("Fixed solar holidays are correctly identified in 2024")
    void testSolarHolidays2024() {
        assertTrue(calculator.isHoliday(LocalDate.of(2024, 1, 1))); // 신정
        assertTrue(calculator.isHoliday(LocalDate.of(2024, 3, 1))); // 삼일절
        assertTrue(calculator.isHoliday(LocalDate.of(2024, 5, 5))); // 어린이날
        assertTrue(calculator.isHoliday(LocalDate.of(2024, 6, 6))); // 현충일
        assertTrue(calculator.isHoliday(LocalDate.of(2024, 8, 15))); // 광복절
        assertTrue(calculator.isHoliday(LocalDate.of(2024, 10, 3))); // 개천절
        assertTrue(calculator.isHoliday(LocalDate.of(2024, 10, 9))); // 한글날
        assertTrue(calculator.isHoliday(LocalDate.of(2024, 12, 25))); // 기독탄신일

        assertFalse(calculator.isHoliday(LocalDate.of(2024, 4, 1))); // 일반 평일
        assertFalse(calculator.isHoliday(LocalDate.of(2024, 7, 17))); // 제헌절 (현재 공휴일 아님)
    }

    @Test
    @DisplayName("Substitute holiday for 2024 Children's Day (Sunday 5/5 -> Monday 5/6)")
    void testChildrensDaySubstitute2024() {
        LocalDate childrensDay = LocalDate.of(2024, 5, 5);
        LocalDate substituteDay = LocalDate.of(2024, 5, 6);

        assertTrue(calculator.isHoliday(childrensDay));
        assertFalse(calculator.isSubstituteHoliday(childrensDay));

        assertTrue(calculator.isHoliday(substituteDay));
        assertTrue(calculator.isSubstituteHoliday(substituteDay));

        Optional<Holiday> holidayOpt = calculator.getHoliday(substituteDay);
        assertTrue(holidayOpt.isPresent());
        assertTrue(holidayOpt.get().name().contains("어린이날"));
        assertEquals(HolidayType.SUBSTITUTE, holidayOpt.get().type());
    }

    @Test
    @DisplayName("2024 Seollal holiday and substitute (2/9 ~ 2/11 Sun -> 2/12 Mon substitute)")
    void testSeollal2024() {
        LocalDate eve = LocalDate.of(2024, 2, 9);
        LocalDate day = LocalDate.of(2024, 2, 10);
        LocalDate next = LocalDate.of(2024, 2, 11); // Sunday
        LocalDate sub = LocalDate.of(2024, 2, 12); // Monday substitute

        assertTrue(calculator.isHoliday(eve));
        assertTrue(calculator.isHoliday(day));
        assertTrue(calculator.isHoliday(next));
        assertTrue(calculator.isHoliday(sub));
        assertTrue(calculator.isSubstituteHoliday(sub));
    }

    @Test
    @DisplayName("2023 Buddha's Birthday Saturday 5/27 -> Monday 5/29 substitute")
    void testBuddhaBirthdaySubstitute2023() {
        LocalDate buddha = LocalDate.of(2023, 5, 27); // Saturday
        LocalDate substitute = LocalDate.of(2023, 5, 29); // Monday

        assertTrue(calculator.isHoliday(buddha));
        assertTrue(calculator.isHoliday(substitute));
        assertTrue(calculator.isSubstituteHoliday(substitute));
    }

    @Test
    @DisplayName("2025 Children's Day and Buddha's Birthday collide on Monday 5/5 -> 5/6 Tuesday substitute")
    void test2025Collision() {
        LocalDate may5 = LocalDate.of(2025, 5, 5);
        LocalDate may6 = LocalDate.of(2025, 5, 6);

        assertTrue(calculator.isHoliday(may5));
        assertTrue(calculator.isHoliday(may6));
        assertTrue(calculator.isSubstituteHoliday(may6));
    }

    @Test
    @DisplayName("Non-substitute holidays (신정, 현충일) do not generate substitute holidays")
    void testNoSubstituteForNewYearAndMemorialDay() {
        // 2021 현충일 (2021-06-06 was Sunday) -> should NOT have substitute holiday on 2021-06-07
        assertFalse(calculator.isSubstituteHoliday(LocalDate.of(2021, 6, 7)));
        assertFalse(calculator.isHoliday(LocalDate.of(2021, 6, 7)));

        // 2023 신정 (2023-01-01 was Sunday) -> should NOT have substitute holiday on 2023-01-02
        assertFalse(calculator.isSubstituteHoliday(LocalDate.of(2023, 1, 2)));
        assertFalse(calculator.isHoliday(LocalDate.of(2023, 1, 2)));
    }

    @Test
    @DisplayName("Range query for holidays getHolidays(start, end)")
    void testRangeQuery() {
        LocalDate start = LocalDate.of(2024, 5, 1);
        LocalDate end = LocalDate.of(2024, 5, 31);

        List<Holiday> mayHolidays = calculator.getHolidays(start, end);
        // May 2024: 5/5 (어린이날), 5/6 (대체공휴일), 5/15 (부처님오신날)
        assertEquals(3, mayHolidays.size());
        assertTrue(mayHolidays.stream().anyMatch(h -> h.date().equals(LocalDate.of(2024, 5, 5))));
        assertTrue(mayHolidays.stream().anyMatch(h -> h.date().equals(LocalDate.of(2024, 5, 6))));
        assertTrue(mayHolidays.stream().anyMatch(h -> h.date().equals(LocalDate.of(2024, 5, 15))));
    }

    @Test
    @DisplayName("Custom holiday addition via Builder")
    void testCustomHoliday() {
        LocalDate electionDay = LocalDate.of(2024, 4, 10);
        KoreanHolidayCalculator customCalc = KoreanHolidayCalculator.builder()
                .addCustomHoliday(electionDay, "제22대 국회의원 선거일")
                .build();

        assertTrue(customCalc.isHoliday(electionDay));
        Optional<Holiday> hol = customCalc.getHoliday(electionDay);
        assertTrue(hol.isPresent());
        assertEquals("제22대 국회의원 선거일", hol.get().name());
        assertEquals(HolidayType.CUSTOM, hol.get().type());

        // Default calc does not have this custom holiday
        assertFalse(calculator.isHoliday(electionDay));
    }

    @Test
    @DisplayName("Holiday record properties and comparison")
    void testHolidayRecord() {
        Holiday h1 = new Holiday("삼일절", LocalDate.of(2024, 3, 1), false);
        Holiday h2 = new Holiday("어린이날", LocalDate.of(2024, 5, 5), false);
        Holiday h3 = new Holiday("삼일절", LocalDate.of(2024, 3, 1), false);

        assertEquals(h1, h3);
        assertTrue(h1.compareTo(h2) < 0);
        assertEquals(HolidayType.SOLAR, h1.type());
        assertFalse(h1.isSubstitute());
    }
}
