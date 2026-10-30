package com.msp.starter.datetime;

import com.msp.bizday.BusinessDayCalculator;
import com.msp.dday.DDayResult;
import com.msp.solarlunar.LunarDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeKitTest {

    @Test
    @DisplayName("Integrated date time kit facade test")
    void testDateTimeKit() {
        // Holiday
        assertTrue(DateTimeKit.isKoreanHoliday(LocalDate.of(2024, 1, 1)));

        // Business Day
        BusinessDayCalculator biz = DateTimeKit.createKoreanBusinessDayCalculator();
        assertTrue(biz.isBusinessDay(LocalDate.of(2024, 5, 3))); // 금요일

        // Time ago
        String ago = DateTimeKit.formatTimeAgo(Instant.now().minusSeconds(10), Locale.KOREAN);
        assertNotNull(ago);

        // Iso Parse
        assertTrue(DateTimeKit.parseIsoTime("2026-10-30T12:00:00Z").isPresent());

        // Cron describe
        assertEquals("매 시 정각", DateTimeKit.describeCron("0 * * * *"));

        // D-Day
        LocalDate base = LocalDate.of(2026, 10, 30);
        DDayResult dday = DateTimeKit.calculateDDay(base.plusDays(3), base);
        assertEquals("D-3", dday.label());

        // Lunar
        LunarDate lunar = DateTimeKit.toLunarDate(LocalDate.of(2024, 2, 10)); // 설날
        assertEquals(1, lunar.month());
        assertEquals(1, lunar.day());
    }
}
