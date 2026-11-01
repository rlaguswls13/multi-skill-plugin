package com.msp.cronbiz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CronBusinessDayAdjuster Unit Tests")
class CronBusinessDayAdjusterTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("Weekday execution remains unchanged under all roll policies")
    void testWeekdayUnchanged() {
        CronBusinessDayAdjuster adjuster = new CronBusinessDayAdjuster();

        // 2026-11-04 is Wednesday
        ZonedDateTime wednesday = ZonedDateTime.of(
                LocalDate.of(2026, 11, 4),
                LocalTime.of(9, 30),
                SEOUL_ZONE
        );

        assertTrue(adjuster.isBusinessDay(wednesday.toLocalDate()));

        Optional<ZonedDateTime> forward = adjuster.adjust(wednesday, BusinessDayRollPolicy.ROLL_FORWARD);
        Optional<ZonedDateTime> backward = adjuster.adjust(wednesday, BusinessDayRollPolicy.ROLL_BACKWARD);
        Optional<ZonedDateTime> skip = adjuster.adjust(wednesday, BusinessDayRollPolicy.SKIP);

        assertEquals(Optional.of(wednesday), forward);
        assertEquals(Optional.of(wednesday), backward);
        assertEquals(Optional.of(wednesday), skip);
    }

    @Test
    @DisplayName("Weekend execution rolls forward to Monday or backward to Friday")
    void testWeekendRolling() {
        CronBusinessDayAdjuster adjuster = new CronBusinessDayAdjuster();

        // 2026-11-07 is Saturday
        ZonedDateTime saturday = ZonedDateTime.of(
                LocalDate.of(2026, 11, 7),
                LocalTime.of(14, 0),
                SEOUL_ZONE
        );
        // 2026-11-08 is Sunday
        ZonedDateTime sunday = ZonedDateTime.of(
                LocalDate.of(2026, 11, 8),
                LocalTime.of(14, 0),
                SEOUL_ZONE
        );

        assertFalse(adjuster.isBusinessDay(saturday.toLocalDate()));
        assertFalse(adjuster.isBusinessDay(sunday.toLocalDate()));

        // Roll forward -> Monday 2026-11-09
        LocalDate mondayDate = LocalDate.of(2026, 11, 9);
        Optional<ZonedDateTime> satForward = adjuster.adjust(saturday, BusinessDayRollPolicy.ROLL_FORWARD);
        Optional<ZonedDateTime> sunForward = adjuster.adjust(sunday, BusinessDayRollPolicy.ROLL_FORWARD);

        assertTrue(satForward.isPresent());
        assertEquals(mondayDate, satForward.get().toLocalDate());
        assertEquals(LocalTime.of(14, 0), satForward.get().toLocalTime());
        assertEquals(SEOUL_ZONE, satForward.get().getZone());

        assertTrue(sunForward.isPresent());
        assertEquals(mondayDate, sunForward.get().toLocalDate());

        // Roll backward -> Friday 2026-11-06
        LocalDate fridayDate = LocalDate.of(2026, 11, 6);
        Optional<ZonedDateTime> satBackward = adjuster.adjust(saturday, BusinessDayRollPolicy.ROLL_BACKWARD);
        Optional<ZonedDateTime> sunBackward = adjuster.adjust(sunday, BusinessDayRollPolicy.ROLL_BACKWARD);

        assertTrue(satBackward.isPresent());
        assertEquals(fridayDate, satBackward.get().toLocalDate());

        assertTrue(sunBackward.isPresent());
        assertEquals(fridayDate, sunBackward.get().toLocalDate());

        // SKIP policy
        assertEquals(Optional.empty(), adjuster.adjust(saturday, BusinessDayRollPolicy.SKIP));
        assertEquals(Optional.empty(), adjuster.adjust(sunday, BusinessDayRollPolicy.SKIP));
    }

    @Test
    @DisplayName("Custom holidays handling with skip and roll")
    void testCustomHolidays() {
        // Christmas 2026-12-25 (Friday) is a holiday
        LocalDate christmas = LocalDate.of(2026, 12, 25);
        CronBusinessDayAdjuster adjuster = new CronBusinessDayAdjuster(Set.of(christmas));

        assertFalse(adjuster.isBusinessDay(christmas));
        assertTrue(adjuster.holidays().contains(christmas));

        ZonedDateTime scheduled = ZonedDateTime.of(christmas, LocalTime.of(10, 0), SEOUL_ZONE);

        // Friday (holiday), Sat (weekend), Sun (weekend) -> next business day is Monday 2026-12-28
        Optional<ZonedDateTime> forward = adjuster.adjust(scheduled, BusinessDayRollPolicy.ROLL_FORWARD);
        assertTrue(forward.isPresent());
        assertEquals(LocalDate.of(2026, 12, 28), forward.get().toLocalDate());

        // Backward -> Thursday 2026-12-24
        Optional<ZonedDateTime> backward = adjuster.adjust(scheduled, BusinessDayRollPolicy.ROLL_BACKWARD);
        assertTrue(backward.isPresent());
        assertEquals(LocalDate.of(2026, 12, 24), backward.get().toLocalDate());

        // Skip
        assertEquals(Optional.empty(), adjuster.adjust(scheduled, BusinessDayRollPolicy.SKIP));
    }

    @Test
    @DisplayName("Chain of consecutive holidays spanning weekends")
    void testConsecutiveHolidaysChain() {
        // Bridge holidays: Thursday 2026-10-01, Friday 2026-10-02 (National holidays)
        // Weekend: Saturday 10-03, Sunday 10-04
        // Monday 2026-10-05 (Substitute holiday)
        Set<LocalDate> holidays = Set.of(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 5)
        );
        CronBusinessDayAdjuster adjuster = new CronBusinessDayAdjuster(holidays);

        // Saturday 2026-10-03 execution
        ZonedDateTime sat = ZonedDateTime.of(LocalDate.of(2026, 10, 3), LocalTime.of(8, 0), SEOUL_ZONE);

        // Forward must skip Sun(10-04) and Mon(10-05) to Tuesday 2026-10-06
        Optional<ZonedDateTime> forward = adjuster.adjust(sat, BusinessDayRollPolicy.ROLL_FORWARD);
        assertTrue(forward.isPresent());
        assertEquals(LocalDate.of(2026, 10, 6), forward.get().toLocalDate());

        // Backward must skip Fri(10-02) and Thu(10-01) to Wednesday 2026-09-30
        Optional<ZonedDateTime> backward = adjuster.adjust(sat, BusinessDayRollPolicy.ROLL_BACKWARD);
        assertTrue(backward.isPresent());
        assertEquals(LocalDate.of(2026, 9, 30), backward.get().toLocalDate());
    }

    @Test
    @DisplayName("Leap year business day navigation")
    void testLeapYear() {
        CronBusinessDayAdjuster adjuster = new CronBusinessDayAdjuster();

        // 2024 is leap year: Feb 28 (Wed), Feb 29 (Thu), Mar 1 (Fri), Mar 2 (Sat)
        LocalDate feb28 = LocalDate.of(2024, 2, 28);
        LocalDate feb29 = LocalDate.of(2024, 2, 29);
        LocalDate mar1 = LocalDate.of(2024, 3, 1);

        assertTrue(adjuster.isBusinessDay(feb29));
        assertEquals(feb29, adjuster.nextBusinessDay(feb28));
        assertEquals(mar1, adjuster.nextBusinessDay(feb29));
        assertEquals(feb28, adjuster.previousBusinessDay(feb29));
    }

    @Test
    @DisplayName("LocalDateTime overload and helper methods")
    void testLocalDateTimeAndHelpers() {
        CronBusinessDayAdjuster adjuster = new CronBusinessDayAdjuster();

        LocalDateTime sundayNoon = LocalDateTime.of(2026, 11, 8, 12, 0);
        Optional<LocalDateTime> rolled = adjuster.adjust(sundayNoon, BusinessDayRollPolicy.ROLL_FORWARD);

        assertTrue(rolled.isPresent());
        assertEquals(LocalDate.of(2026, 11, 9), rolled.get().toLocalDate());
        assertEquals(LocalTime.of(12, 0), rolled.get().toLocalTime());

        // nextOrSame / previousOrSame
        assertEquals(LocalDate.of(2026, 11, 6), adjuster.previousOrSameBusinessDay(LocalDate.of(2026, 11, 6))); // Friday
        assertEquals(LocalDate.of(2026, 11, 6), adjuster.previousOrSameBusinessDay(LocalDate.of(2026, 11, 7))); // Saturday
        assertEquals(LocalDate.of(2026, 11, 9), adjuster.nextOrSameBusinessDay(LocalDate.of(2026, 11, 8))); // Sunday
    }

    @Test
    @DisplayName("Null arguments validation")
    void testValidation() {
        CronBusinessDayAdjuster adjuster = new CronBusinessDayAdjuster();

        assertThrows(NullPointerException.class, () -> adjuster.isBusinessDay(null));
        assertThrows(NullPointerException.class, () -> adjuster.nextBusinessDay(null));
        assertThrows(NullPointerException.class, () -> adjuster.previousBusinessDay(null));
        assertThrows(NullPointerException.class, () -> adjuster.adjust((ZonedDateTime) null, BusinessDayRollPolicy.ROLL_FORWARD));
        assertThrows(NullPointerException.class, () -> adjuster.adjust(ZonedDateTime.now(), null));
    }
}
