package com.msp.lunarholiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KoreanHolidayCalendarTest {

    private final KoreanHolidayCalendar calendar = KoreanHolidayCalendar.getInstance();

    @Test
    @DisplayName("2024 Korean Lunar Holidays (설날, 추석) and Substitute Holidays")
    void test2024Holidays() {
        List<KoreanHoliday> holidays = calendar.getHolidaysForYear(2024);
        assertFalse(holidays.isEmpty());

        // 설날 연휴: 2024-02-09, 2024-02-10, 2024-02-11 (일요일) -> 대체공휴일 2024-02-12 (월요일)
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 2, 9)));
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 2, 10)));
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 2, 11)));
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 2, 12)));

        Optional<KoreanHoliday> subSeollal = calendar.getHoliday(LocalDate.of(2024, 2, 12));
        assertTrue(subSeollal.isPresent());
        assertTrue(subSeollal.get().isSubstitute());

        // 부처님오신날: 2024-05-15 (수요일)
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 5, 15)));

        // 어린이날: 2024-05-05 (일요일) -> 대체공휴일 2024-05-06 (월요일)
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 5, 5)));
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 5, 6)));

        // 추석 연휴: 2024-09-16, 2024-09-17, 2024-09-18
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 9, 16)));
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 9, 17)));
        assertTrue(calendar.isHoliday(LocalDate.of(2024, 9, 18)));
    }

    @Test
    @DisplayName("2023 Buddha's Birthday and Seollal substitute holidays")
    void test2023Holidays() {
        // 설날 연휴: 2023-01-21(토), 2023-01-22(일), 2023-01-23(월) -> 대체공휴일 2023-01-24(화)
        assertTrue(calendar.isHoliday(LocalDate.of(2023, 1, 24)));

        // 부처님오신날: 2023-05-27 (토요일) -> 대체공휴일 2023-05-29 (월요일)
        assertTrue(calendar.isHoliday(LocalDate.of(2023, 5, 27)));
        assertTrue(calendar.isHoliday(LocalDate.of(2023, 5, 29)));
    }

    @Test
    @DisplayName("Cron / Schedule Adjustment on Holidays and Weekends")
    void testScheduleAdjustment() {
        // 2024-02-10 is Seollal (Saturday and public holiday)
        LocalDate target = LocalDate.of(2024, 2, 10);

        // ROLL_FORWARD: should skip Sun(2/11), Mon(2/12 holiday) -> lands on Tue(2/13)
        LocalDate nextBizDay = calendar.adjust(target, AdjustmentPolicy.ROLL_FORWARD);
        assertEquals(LocalDate.of(2024, 2, 13), nextBizDay);
        assertTrue(calendar.isBusinessDay(nextBizDay));

        // ROLL_BACKWARD: should skip Fri(2/9 holiday) -> lands on Thu(2/8)
        LocalDate prevBizDay = calendar.adjust(target, AdjustmentPolicy.ROLL_BACKWARD);
        assertEquals(LocalDate.of(2024, 2, 8), prevBizDay);
        assertTrue(calendar.isBusinessDay(prevBizDay));

        // SKIP policy returns null
        LocalDate skipped = calendar.adjust(target, AdjustmentPolicy.SKIP);
        assertNull(skipped);

        // Normal business day (e.g. 2024-02-14 Wednesday) remains unchanged
        LocalDate regularWednesday = LocalDate.of(2024, 2, 14);
        assertEquals(regularWednesday, calendar.adjust(regularWednesday, AdjustmentPolicy.ROLL_FORWARD));
    }

    @Test
    @DisplayName("Compute next N business days")
    void testNextBusinessDays() {
        // From Thursday 2024-02-08, next 2 business days:
        // Fri(2/9 holiday), Sat(2/10), Sun(2/11), Mon(2/12 holiday)
        // Day 1: Tue 2024-02-13
        // Day 2: Wed 2024-02-14
        List<LocalDate> next2 = calendar.nextBusinessDays(LocalDate.of(2024, 2, 8), 2);
        assertEquals(2, next2.size());
        assertEquals(LocalDate.of(2024, 2, 13), next2.get(0));
        assertEquals(LocalDate.of(2024, 2, 14), next2.get(1));
    }
}
