package com.msp.fiscal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

class FiscalCalendarTest {

    @Test
    @DisplayName("Standard calendar year (January start)")
    void testStandardCalendar() {
        FiscalCalendar fc = FiscalCalendar.standard();
        LocalDate date = LocalDate.of(2026, 3, 15);

        FiscalYear fy = fc.getFiscalYear(date);
        assertEquals(2026, fy.year());
        assertEquals("FY2026", fy.code());
        assertEquals(LocalDate.of(2026, 1, 1), fy.startDate());
        assertEquals(LocalDate.of(2026, 12, 31), fy.endDate());
        assertTrue(fy.contains(date));

        FiscalQuarter fq = fc.getFiscalQuarter(date);
        assertEquals(1, fq.quarterNumber());
        assertEquals("FY2026-Q1", fq.code());
        assertEquals(LocalDate.of(2026, 1, 1), fq.startDate());
        assertEquals(LocalDate.of(2026, 3, 31), fq.endDate());
        assertTrue(fq.contains(date));

        FiscalHalf fh = fc.getFiscalHalf(date);
        assertEquals(1, fh.halfNumber());
        assertEquals("FY2026-H1", fh.code());
        assertEquals(LocalDate.of(2026, 1, 1), fh.startDate());
        assertEquals(LocalDate.of(2026, 6, 30), fh.endDate());

        assertEquals(3, fc.getFiscalMonth(date));
        assertEquals(11, fc.getFiscalWeek(date));
    }

    @Test
    @DisplayName("US Federal Government calendar (October start, END_YEAR)")
    void testUSFederalCalendar() {
        FiscalCalendar fc = FiscalCalendar.usFederal();

        // 2026-10-15 is in FY2027-Q1
        LocalDate d1 = LocalDate.of(2026, 10, 15);
        FiscalYear fy1 = fc.getFiscalYear(d1);
        assertEquals(2027, fy1.year());
        assertEquals("FY2027", fy1.code());
        assertEquals(LocalDate.of(2026, 10, 1), fy1.startDate());
        assertEquals(LocalDate.of(2027, 9, 30), fy1.endDate());
        assertTrue(fy1.contains(d1));

        FiscalQuarter fq1 = fc.getFiscalQuarter(d1);
        assertEquals(1, fq1.quarterNumber());
        assertEquals("FY2027-Q1", fq1.code());
        assertEquals(LocalDate.of(2026, 10, 1), fq1.startDate());
        assertEquals(LocalDate.of(2026, 12, 31), fq1.endDate());
        assertEquals(1, fc.getFiscalMonth(d1));
        assertEquals(3, fc.getFiscalWeek(d1));

        // 2027-01-10 is in FY2027-Q2
        LocalDate d2 = LocalDate.of(2027, 1, 10);
        FiscalQuarter fq2 = fc.getFiscalQuarter(d2);
        assertEquals(2, fq2.quarterNumber());
        assertEquals("FY2027-Q2", fq2.code());
        assertEquals(LocalDate.of(2027, 1, 1), fq2.startDate());
        assertEquals(LocalDate.of(2027, 3, 31), fq2.endDate());

        // 2027-09-30 is last day of FY2027-Q4
        LocalDate d3 = LocalDate.of(2027, 9, 30);
        FiscalQuarter fq3 = fc.getFiscalQuarter(d3);
        assertEquals(4, fq3.quarterNumber());
        assertEquals("FY2027-Q4", fq3.code());
        assertEquals(LocalDate.of(2027, 7, 1), fq3.startDate());
        assertEquals(LocalDate.of(2027, 9, 30), fq3.endDate());
        assertEquals(12, fc.getFiscalMonth(d3));
    }

    @Test
    @DisplayName("UK / Japan fiscal calendar (April start, END_YEAR)")
    void testUKJapanCalendar() {
        FiscalCalendar fc = FiscalCalendar.ukJapan();

        // 2026-03-31 is last day of FY2026
        LocalDate lastDayFY26 = LocalDate.of(2026, 3, 31);
        FiscalYear fy26 = fc.getFiscalYear(lastDayFY26);
        assertEquals(2026, fy26.year());
        assertEquals(LocalDate.of(2025, 4, 1), fy26.startDate());
        assertEquals(LocalDate.of(2026, 3, 31), fy26.endDate());
        assertEquals(4, fc.getFiscalQuarter(lastDayFY26).quarterNumber());

        // 2026-04-01 is first day of FY2027
        LocalDate firstDayFY27 = LocalDate.of(2026, 4, 1);
        FiscalYear fy27 = fc.getFiscalYear(firstDayFY27);
        assertEquals(2027, fy27.year());
        assertEquals(LocalDate.of(2026, 4, 1), fy27.startDate());
        assertEquals(LocalDate.of(2027, 3, 31), fy27.endDate());
        assertEquals(1, fc.getFiscalQuarter(firstDayFY27).quarterNumber());
    }

    @Test
    @DisplayName("START_YEAR convention naming")
    void testStartYearConvention() {
        FiscalCalendar fc = FiscalCalendar.of(Month.OCTOBER, YearNamingConvention.START_YEAR);
        LocalDate date = LocalDate.of(2026, 10, 15);

        FiscalYear fy = fc.getFiscalYear(date);
        assertEquals(2026, fy.year());
        assertEquals("FY2026", fy.code());
        assertEquals(LocalDate.of(2026, 10, 1), fy.startDate());
        assertEquals(LocalDate.of(2027, 9, 30), fy.endDate());
    }

    @Test
    @DisplayName("Direct year and quarter queries")
    void testDirectQueries() {
        FiscalCalendar fc = FiscalCalendar.usFederal();

        FiscalYear fy2028 = fc.getYear(2028);
        assertEquals(2028, fy2028.year());
        assertEquals(LocalDate.of(2027, 10, 1), fy2028.startDate());
        assertEquals(LocalDate.of(2028, 9, 30), fy2028.endDate());

        FiscalQuarter q3 = fc.getQuarter(2028, 3);
        assertEquals(3, q3.quarterNumber());
        assertEquals("FY2028-Q3", q3.code());
        assertEquals(LocalDate.of(2028, 4, 1), q3.startDate());
        assertEquals(LocalDate.of(2028, 6, 30), q3.endDate());

        assertThrows(IllegalArgumentException.class, () -> fc.getQuarter(2028, 0));
        assertThrows(IllegalArgumentException.class, () -> fc.getQuarter(2028, 5));
    }
}
