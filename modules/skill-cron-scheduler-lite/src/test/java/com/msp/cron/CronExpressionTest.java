package com.msp.cron;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CronExpressionTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    @DisplayName("Should parse standard 5-field Unix cron and calculate next trigger")
    void testStandardFiveFieldCron() {
        // Every 5 minutes
        CronExpression cron = CronExpression.parse("*/5 * * * *");
        ZonedDateTime from = ZonedDateTime.of(2026, 10, 29, 10, 2, 30, 0, UTC);

        ZonedDateTime next = cron.next(from);
        assertEquals(ZonedDateTime.of(2026, 10, 29, 10, 5, 0, 0, UTC), next);

        List<ZonedDateTime> nextThree = cron.next(from, 3);
        assertEquals(List.of(
                ZonedDateTime.of(2026, 10, 29, 10, 5, 0, 0, UTC),
                ZonedDateTime.of(2026, 10, 29, 10, 10, 0, 0, UTC),
                ZonedDateTime.of(2026, 10, 29, 10, 15, 0, 0, UTC)
        ), nextThree);
    }

    @Test
    @DisplayName("Should parse 6-field Quartz cron with seconds")
    void testSixFieldQuartzCron() {
        // Every 15 seconds: 0, 15, 30, 45
        CronExpression cron = CronExpression.parse("*/15 * * * * *");
        ZonedDateTime from = ZonedDateTime.of(2026, 10, 29, 12, 0, 10, 0, UTC);

        ZonedDateTime next = cron.next(from);
        assertEquals(ZonedDateTime.of(2026, 10, 29, 12, 0, 15, 0, UTC), next);
        assertTrue(cron.matches(next));
    }

    @Test
    @DisplayName("Should support weekday names and range (e.g. MON-FRI)")
    void testWeekdayNames() {
        // At 09:00 on weekdays Monday through Friday
        CronExpression cron = CronExpression.parse("0 9 * * MON-FRI");

        // Friday 2026-10-30 at 09:01
        ZonedDateTime fridayAfter9 = ZonedDateTime.of(2026, 10, 30, 9, 1, 0, 0, UTC);
        ZonedDateTime next = cron.next(fridayAfter9);

        // Next should jump over Saturday and Sunday to Monday 2026-11-02 at 09:00
        assertEquals(ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, UTC), next);
        assertTrue(cron.matches(next));
    }

    @Test
    @DisplayName("Should accurately handle leap years (Feb 29)")
    void testLeapYearHandling() {
        // Feb 29 at midnight
        CronExpression cron = CronExpression.parse("0 0 0 29 2 ?");

        // From 2027 (non-leap year)
        ZonedDateTime from2027 = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, UTC);
        ZonedDateTime nextLeap = cron.next(from2027);

        // 2028 is a leap year!
        assertEquals(ZonedDateTime.of(2028, 2, 29, 0, 0, 0, 0, UTC), nextLeap);
        assertTrue(cron.matches(nextLeap));

        // Next leap year trigger after 2028 is 2032 (2029, 2030, 2031 are not leap years)
        ZonedDateTime nextNextLeap = cron.next(nextLeap);
        assertEquals(ZonedDateTime.of(2032, 2, 29, 0, 0, 0, 0, UTC), nextNextLeap);
    }

    @Test
    @DisplayName("Should support month boundary transitions")
    void testMonthBoundaryTransition() {
        // First second of the month: 0 0 0 1 * ?
        CronExpression cron = CronExpression.parse("0 0 0 1 * ?");
        ZonedDateTime lastDayOfOct = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 0, UTC);

        ZonedDateTime next = cron.next(lastDayOfOct);
        assertEquals(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, UTC), next);
    }

    @Test
    @DisplayName("Should test matches() logic directly")
    void testMatches() {
        CronExpression cron = CronExpression.parse("0 30 14 ? * *"); // 14:30:00 every day
        ZonedDateTime matchTime = ZonedDateTime.of(2026, 10, 29, 14, 30, 0, 0, UTC);
        ZonedDateTime nonMatchTime = ZonedDateTime.of(2026, 10, 29, 14, 30, 1, 0, UTC);

        assertTrue(cron.matches(matchTime));
        assertFalse(cron.matches(nonMatchTime));
    }

    @Test
    @DisplayName("Should execute scheduled task and support cancellation")
    void testSchedulerExecutionAndCancellation() throws Exception {
        LightweightCronScheduler scheduler = LightweightCronScheduler.create();
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger counter = new AtomicInteger();

        // Every second: */1 * * * * *
        ScheduledTask task = scheduler.schedule("*/1 * * * * *", () -> {
            counter.incrementAndGet();
            latch.countDown();
        });

        assertNotNull(task.taskId());
        assertFalse(task.isCancelled());

        boolean executed = latch.await(4, TimeUnit.SECONDS);
        assertTrue(executed, "Expected task to execute at least twice");

        task.cancel();
        assertTrue(task.isCancelled());
        int countAtCancel = counter.get();

        Thread.sleep(1500);
        assertEquals(countAtCancel, counter.get(), "Task should not run after cancellation");

        scheduler.shutdown();
        assertTrue(scheduler.isShutdown());
    }

    @Test
    @DisplayName("Should throw CronException on invalid cron expressions")
    void testInvalidCronExpressions() {
        assertThrows(CronException.class, () -> CronExpression.parse("invalid"));
        assertThrows(CronException.class, () -> CronExpression.parse("0 0 0 0")); // 4 fields
        assertThrows(CronException.class, () -> CronExpression.parse("70 * * * *")); // minute > 59
        assertThrows(CronException.class, () -> CronExpression.parse("* 25 * * *")); // hour > 23
        assertThrows(CronException.class, () -> CronExpression.parse("* * * 13 *")); // month > 12
    }
}
