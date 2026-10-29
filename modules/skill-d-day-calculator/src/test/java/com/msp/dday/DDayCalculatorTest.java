package com.msp.dday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DDayCalculatorTest {

    private final LocalDate base = LocalDate.of(2026, 10, 30);

    @Test
    @DisplayName("D-Day calculation for today, future, and past dates")
    void testDDayCalculations() {
        // Today
        DDayResult today = DDayCalculator.calculate(base, base);
        assertEquals("D-Day", today.label());
        assertEquals(0, today.daysDiff());
        assertTrue(today.isToday());
        assertFalse(today.isFuture());
        assertFalse(today.isPast());
        assertEquals(0, today.remainingDays());
        assertEquals(0, today.elapsedDays());
        assertEquals("오늘 (D-Day)", today.toKoreanSummary());

        // Future: Tomorrow (D-1)
        DDayResult tomorrow = DDayCalculator.calculate(base.plusDays(1), base);
        assertEquals("D-1", tomorrow.label());
        assertEquals(1, tomorrow.daysDiff());
        assertFalse(tomorrow.isToday());
        assertTrue(tomorrow.isFuture());
        assertFalse(tomorrow.isPast());
        assertEquals(1, tomorrow.remainingDays());
        assertEquals("1일 남음 (D-1)", tomorrow.toKoreanSummary());

        // Future: In 7 days (D-7)
        DDayResult in7Days = DDayCalculator.calculate(base.plusDays(7), base);
        assertEquals("D-7", in7Days.label());
        assertEquals(7, in7Days.daysDiff());
        assertEquals(7, in7Days.remainingDays());

        // Past: Yesterday (D+1)
        DDayResult yesterday = DDayCalculator.calculate(base.minusDays(1), base);
        assertEquals("D+1", yesterday.label());
        assertEquals(-1, yesterday.daysDiff());
        assertTrue(yesterday.isPast());
        assertEquals(1, yesterday.elapsedDays());
        assertEquals("1일 지남 (D+1)", yesterday.toKoreanSummary());

        // Past: 7 days ago (D+7)
        DDayResult past7Days = DDayCalculator.calculate(base.minusDays(7), base);
        assertEquals("D+7", past7Days.label());
        assertEquals(-7, past7Days.daysDiff());
        assertEquals(7, past7Days.elapsedDays());
    }

    @Test
    @DisplayName("Korean legal age (만 나이) calculation before and on birthday")
    void testManAge() {
        LocalDate birth = LocalDate.of(2000, 5, 15);

        // One day before 26th birthday -> 25
        assertEquals(25, DDayCalculator.calculateManAge(birth, LocalDate.of(2026, 5, 14)));

        // On 26th birthday -> 26
        assertEquals(26, DDayCalculator.calculateManAge(birth, LocalDate.of(2026, 5, 15)));

        // After 26th birthday -> 26
        assertEquals(26, DDayCalculator.calculateManAge(birth, LocalDate.of(2026, 5, 16)));

        // Leap year birthday handling
        LocalDate leapBirth = LocalDate.of(2024, 2, 29);
        assertEquals(0, DDayCalculator.calculateManAge(leapBirth, LocalDate.of(2025, 2, 27)));
        assertEquals(1, DDayCalculator.calculateManAge(leapBirth, LocalDate.of(2025, 3, 1)));
        assertEquals(2, DDayCalculator.calculateManAge(leapBirth, LocalDate.of(2026, 3, 1)));
    }

    @Test
    @DisplayName("Calendar year age (연 나이) and traditional counting age (세는 나이)")
    void testYearAndCountingAge() {
        LocalDate birth = LocalDate.of(2000, 12, 31);
        LocalDate reference = LocalDate.of(2026, 1, 1);

        // 연 나이: 2026 - 2000 = 26
        assertEquals(26, DDayCalculator.calculateYearAge(birth, reference));

        // 만 나이 at Jan 1, 2026: 25
        assertEquals(25, DDayCalculator.calculateManAge(birth, reference));

        // 세는 나이: 2026 - 2000 + 1 = 27
        assertEquals(27, DDayCalculator.calculateCountingAge(birth, reference));
    }

    @Test
    @DisplayName("Birth date after base date throws IllegalArgumentException")
    void testBirthDateValidation() {
        LocalDate futureBirth = LocalDate.of(2030, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> DDayCalculator.calculateManAge(futureBirth, base));
        assertThrows(IllegalArgumentException.class, () -> DDayCalculator.calculateYearAge(futureBirth, base));
        assertThrows(IllegalArgumentException.class, () -> DDayCalculator.calculateCountingAge(futureBirth, base));
    }

    @Test
    @DisplayName("Anniversary calculations (100일, 1주년)")
    void testAnniversaries() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        // Korean 100th day anniversary (Day 1 included)
        // Jan: 31 days (Day 1-31), Feb: 28 days (Day 32-59), Mar: 31 days (Day 60-90), Apr 10: (Day 91-100)
        LocalDate day100 = DDayCalculator.anniversary(start, 100);
        assertEquals(LocalDate.of(2026, 4, 10), day100);

        // Offset-based 100 days (+100 calendar days)
        LocalDate day100Offset = DDayCalculator.anniversary(start, 100, false);
        assertEquals(LocalDate.of(2026, 4, 11), day100Offset);

        // 1st Year anniversary
        LocalDate year1 = DDayCalculator.yearAnniversary(start, 1);
        assertEquals(LocalDate.of(2027, 1, 1), year1);

        // Leap year annual anniversary: 2024-02-29 plus 1 year is 2025-02-28
        LocalDate leapStart = LocalDate.of(2024, 2, 29);
        assertEquals(LocalDate.of(2025, 2, 28), DDayCalculator.yearAnniversary(leapStart, 1));
        assertEquals(LocalDate.of(2028, 2, 29), DDayCalculator.yearAnniversary(leapStart, 4));
    }

    @Test
    @DisplayName("Common milestones generation")
    void testCommonMilestones() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        List<Anniversary> milestones = DDayCalculator.commonMilestones(start);

        assertNotNull(milestones);
        assertEquals(10, milestones.size());
        assertEquals("100일", milestones.get(0).name());
        assertEquals(LocalDate.of(2026, 4, 10), milestones.get(0).date());

        assertEquals("1주년", milestones.get(5).name());
        assertEquals(LocalDate.of(2027, 1, 1), milestones.get(5).date());
    }
}
