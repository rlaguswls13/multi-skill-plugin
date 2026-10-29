package com.msp.fiscal;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Configurable fiscal calendar utility providing fiscal year, quarter, half,
 * and week computations for corporate and governmental accounting periods.
 */
public final class FiscalCalendar {

    private final Month startMonth;
    private final YearNamingConvention convention;

    private FiscalCalendar(Month startMonth, YearNamingConvention convention) {
        this.startMonth = Objects.requireNonNull(startMonth, "startMonth must not be null");
        this.convention = Objects.requireNonNull(convention, "convention must not be null");
    }

    // =========================================================================
    // Factory Methods & Standard Presets
    // =========================================================================

    /**
     * Creates a FiscalCalendar with the specified start month using the standard {@link YearNamingConvention#END_YEAR}.
     */
    public static FiscalCalendar of(Month startMonth) {
        return new FiscalCalendar(startMonth, YearNamingConvention.END_YEAR);
    }

    /**
     * Creates a FiscalCalendar with the specified start month and naming convention.
     */
    public static FiscalCalendar of(Month startMonth, YearNamingConvention convention) {
        return new FiscalCalendar(startMonth, convention);
    }

    /**
     * Standard calendar year (January 1 to December 31).
     * Used in South Korea, China, Germany, and general corporate accounting.
     */
    public static FiscalCalendar standard() {
        return of(Month.JANUARY);
    }

    /**
     * United States Federal Government fiscal calendar (October 1 to September 30).
     */
    public static FiscalCalendar usFederal() {
        return of(Month.OCTOBER, YearNamingConvention.END_YEAR);
    }

    /**
     * UK, Japan, Canada, and India standard fiscal calendar (April 1 to March 31).
     */
    public static FiscalCalendar ukJapan() {
        return of(Month.APRIL, YearNamingConvention.END_YEAR);
    }

    /**
     * Australian and New Zealand fiscal calendar (July 1 to June 30).
     */
    public static FiscalCalendar australian() {
        return of(Month.JULY, YearNamingConvention.END_YEAR);
    }

    public Month startMonth() {
        return startMonth;
    }

    public YearNamingConvention convention() {
        return convention;
    }

    // =========================================================================
    // Calculations for a Given Date
    // =========================================================================

    /**
     * Computes the {@link FiscalYear} for the specified calendar date.
     *
     * @param date calendar date
     * @return FiscalYear record containing year number, startDate, and endDate
     */
    public FiscalYear getFiscalYear(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");

        int calYear = date.getYear();
        int calMonth = date.getMonthValue();
        int startMonthVal = startMonth.getValue();

        LocalDate startDate;
        LocalDate endDate;
        int fyNumber;

        if (calMonth >= startMonthVal) {
            startDate = LocalDate.of(calYear, startMonth, 1);
            endDate = LocalDate.of(calYear + 1, startMonth, 1).minusDays(1);
            fyNumber = (convention == YearNamingConvention.END_YEAR)
                    ? (startMonth == Month.JANUARY ? calYear : calYear + 1)
                    : calYear;
        } else {
            startDate = LocalDate.of(calYear - 1, startMonth, 1);
            endDate = LocalDate.of(calYear, startMonth, 1).minusDays(1);
            fyNumber = (convention == YearNamingConvention.END_YEAR)
                    ? calYear
                    : calYear - 1;
        }

        return new FiscalYear(fyNumber, startDate, endDate, startMonth);
    }

    /**
     * Computes the {@link FiscalQuarter} (Q1, Q2, Q3, or Q4) for the specified calendar date.
     *
     * @param date calendar date
     * @return FiscalQuarter record
     */
    public FiscalQuarter getFiscalQuarter(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        FiscalYear fy = getFiscalYear(date);

        int monthOffset = (date.getMonthValue() - startMonth.getValue() + 12) % 12;
        int quarterNumber = (monthOffset / 3) + 1;

        LocalDate qStart = fy.startDate().plusMonths((quarterNumber - 1) * 3L);
        LocalDate qEnd = fy.startDate().plusMonths(quarterNumber * 3L).minusDays(1);

        return new FiscalQuarter(fy.year(), quarterNumber, qStart, qEnd);
    }

    /**
     * Computes the {@link FiscalHalf} (H1 or H2) for the specified calendar date.
     *
     * @param date calendar date
     * @return FiscalHalf record
     */
    public FiscalHalf getFiscalHalf(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        FiscalYear fy = getFiscalYear(date);

        int monthOffset = (date.getMonthValue() - startMonth.getValue() + 12) % 12;
        int halfNumber = (monthOffset / 6) + 1;

        LocalDate hStart = fy.startDate().plusMonths((halfNumber - 1) * 6L);
        LocalDate hEnd = fy.startDate().plusMonths(halfNumber * 6L).minusDays(1);

        return new FiscalHalf(fy.year(), halfNumber, hStart, hEnd);
    }

    /**
     * Computes the 1-based fiscal week number (1 to 52 or 53) from the start of the fiscal year.
     *
     * @param date calendar date
     * @return fiscal week number (1 to 53)
     */
    public int getFiscalWeek(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        FiscalYear fy = getFiscalYear(date);
        long days = ChronoUnit.DAYS.between(fy.startDate(), date);
        return (int) (days / 7) + 1;
    }

    /**
     * Computes the 1-based fiscal month number (1 to 12) from the start of the fiscal year.
     *
     * @param date calendar date
     * @return fiscal month number (1 to 12)
     */
    public int getFiscalMonth(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return ((date.getMonthValue() - startMonth.getValue() + 12) % 12) + 1;
    }

    // =========================================================================
    // Direct Access by Fiscal Year Number
    // =========================================================================

    /**
     * Constructs a {@link FiscalYear} for a specific fiscal year number.
     *
     * @param fiscalYear fiscal year number (e.g. 2027)
     * @return FiscalYear record
     */
    public FiscalYear getYear(int fiscalYear) {
        int calStartYear;
        if (convention == YearNamingConvention.END_YEAR) {
            calStartYear = (startMonth == Month.JANUARY) ? fiscalYear : fiscalYear - 1;
        } else {
            calStartYear = fiscalYear;
        }

        LocalDate startDate = LocalDate.of(calStartYear, startMonth, 1);
        LocalDate endDate = LocalDate.of(calStartYear + 1, startMonth, 1).minusDays(1);
        return new FiscalYear(fiscalYear, startDate, endDate, startMonth);
    }

    /**
     * Constructs a {@link FiscalQuarter} for a specific fiscal year number and quarter number.
     *
     * @param fiscalYear    fiscal year number
     * @param quarterNumber quarter number (1 to 4)
     * @return FiscalQuarter record
     */
    public FiscalQuarter getQuarter(int fiscalYear, int quarterNumber) {
        if (quarterNumber < 1 || quarterNumber > 4) {
            throw new IllegalArgumentException("quarterNumber must be between 1 and 4, got: " + quarterNumber);
        }
        FiscalYear fy = getYear(fiscalYear);
        LocalDate qStart = fy.startDate().plusMonths((quarterNumber - 1) * 3L);
        LocalDate qEnd = fy.startDate().plusMonths(quarterNumber * 3L).minusDays(1);
        return new FiscalQuarter(fiscalYear, quarterNumber, qStart, qEnd);
    }
}
