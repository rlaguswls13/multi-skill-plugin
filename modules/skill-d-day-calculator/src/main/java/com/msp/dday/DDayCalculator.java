package com.msp.dday;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility calculator for D-Day count labels, Korean legal/year/traditional ages,
 * and anniversary milestones (e.g. 100 days, 1 year).
 */
public final class DDayCalculator {

    private DDayCalculator() {
    }

    // =========================================================================
    // D-Day Calculations
    // =========================================================================

    /**
     * Calculates the D-Day result between a target date and today's date.
     *
     * @param target target event date
     * @return DDayResult record
     */
    public static DDayResult calculate(LocalDate target) {
        return calculate(target, LocalDate.now());
    }

    /**
     * Calculates the D-Day result between a target date and a reference base date.
     * <p>
     * Label convention:
     * <ul>
     *   <li>Target is today: <code>D-Day</code></li>
     *   <li>Target is N days in future: <code>D-N</code> (e.g. D-1, D-7)</li>
     *   <li>Target is N days in past: <code>D+N</code> (e.g. D+1, D+7)</li>
     * </ul>
     *
     * @param target target event date
     * @param base   reference baseline date
     * @return DDayResult record
     */
    public static DDayResult calculate(LocalDate target, LocalDate base) {
        Objects.requireNonNull(target, "target date must not be null");
        Objects.requireNonNull(base, "base date must not be null");

        long diff = ChronoUnit.DAYS.between(base, target);
        String label;

        if (diff == 0) {
            label = "D-Day";
        } else if (diff > 0) {
            label = "D-" + diff;
        } else {
            label = "D+" + (-diff);
        }

        return new DDayResult(target, base, diff, label);
    }

    // =========================================================================
    // Age Calculations (만 나이 / 연 나이 / 세는 나이)
    // =========================================================================

    /**
     * Calculates Korean legal / international age (만 나이) as of today.
     *
     * @param birth birth date
     * @return age in completed full years
     */
    public static int calculateManAge(LocalDate birth) {
        return calculateManAge(birth, LocalDate.now());
    }

    /**
     * Calculates Korean legal / international age (만 나이) as of the baseline date.
     * <p>
     * Under the unified age law (만 나이 통일법), age is 0 at birth and increases by 1 on each birthday.
     *
     * @param birth birth date
     * @param base  reference baseline date
     * @return age in completed full years
     * @throws IllegalArgumentException if birth date is after base date
     */
    public static int calculateManAge(LocalDate birth, LocalDate base) {
        validateBirthDate(birth, base);
        return Period.between(birth, base).getYears();
    }

    /**
     * Calculates Korean calendar year age (연 나이) as of today.
     * Used in the Military Service Act and Youth Protection Act.
     *
     * @param birth birth date
     * @return base year minus birth year
     */
    public static int calculateYearAge(LocalDate birth) {
        return calculateYearAge(birth, LocalDate.now());
    }

    /**
     * Calculates Korean calendar year age (연 나이) as of the baseline date:
     * {@code base.getYear() - birth.getYear()}.
     *
     * @param birth birth date
     * @param base  reference baseline date
     * @return base year minus birth year
     * @throws IllegalArgumentException if birth date is after base date
     */
    public static int calculateYearAge(LocalDate birth, LocalDate base) {
        validateBirthDate(birth, base);
        return base.getYear() - birth.getYear();
    }

    /**
     * Calculates traditional Korean counting age (세는 나이) as of today:
     * {@code currentYear - birthYear + 1}.
     *
     * @param birth birth date
     * @return traditional counting age
     */
    public static int calculateCountingAge(LocalDate birth) {
        return calculateCountingAge(birth, LocalDate.now());
    }

    /**
     * Calculates traditional Korean counting age (세는 나이) as of the baseline date:
     * {@code base.getYear() - birth.getYear() + 1}.
     *
     * @param birth birth date
     * @param base  reference baseline date
     * @return traditional counting age
     * @throws IllegalArgumentException if birth date is after base date
     */
    public static int calculateCountingAge(LocalDate birth, LocalDate base) {
        validateBirthDate(birth, base);
        return base.getYear() - birth.getYear() + 1;
    }

    private static void validateBirthDate(LocalDate birth, LocalDate base) {
        Objects.requireNonNull(birth, "birth date must not be null");
        Objects.requireNonNull(base, "base date must not be null");
        if (birth.isAfter(base)) {
            throw new IllegalArgumentException("Birth date (" + birth + ") cannot be after base date (" + base + ")");
        }
    }

    // =========================================================================
    // Anniversary / Milestone Calculations
    // =========================================================================

    /**
     * Calculates the N-th day anniversary from the start date using the standard Korean convention
     * where the starting date counts as Day 1 (e.g. 100일 is {@code start.plusDays(99)}).
     *
     * @param start start date (Day 1)
     * @param days  milestone day count (e.g. 100, 200, 1000)
     * @return calendar date of the milestone
     */
    public static LocalDate anniversary(LocalDate start, int days) {
        return anniversary(start, days, true);
    }

    /**
     * Calculates the N-th day anniversary from the start date, with configurable start date inclusion.
     *
     * @param start            start date
     * @param days             milestone day count (e.g. 100)
     * @param includeStartDate true to count start date as Day 1 (standard Korean style),
     *                         false to advance by exact day offset
     * @return calendar date of the milestone
     */
    public static LocalDate anniversary(LocalDate start, int days, boolean includeStartDate) {
        Objects.requireNonNull(start, "start date must not be null");
        if (days <= 0) {
            throw new IllegalArgumentException("Anniversary days must be positive, got: " + days);
        }
        return includeStartDate ? start.plusDays(days - 1) : start.plusDays(days);
    }

    /**
     * Calculates the N-th year anniversary from the start date (e.g. 1st anniversary, 10th anniversary).
     * Handles leap day (Feb 29) gracefully.
     *
     * @param start start date
     * @param years number of years
     * @return calendar date of the annual milestone
     */
    public static LocalDate yearAnniversary(LocalDate start, int years) {
        Objects.requireNonNull(start, "start date must not be null");
        if (years <= 0) {
            throw new IllegalArgumentException("Anniversary years must be positive, got: " + years);
        }
        return start.plusYears(years);
    }

    /**
     * Generates a common list of milestone anniversaries for couples, newborn babies, or project kickoffs:
     * 100일, 200일, 300일, 500일, 1000일, 1주년, 2주년, 3주년, 5주년, 10주년.
     *
     * @param start start date
     * @return list of calculated milestones
     */
    public static List<Anniversary> commonMilestones(LocalDate start) {
        Objects.requireNonNull(start, "start date must not be null");
        List<Anniversary> list = new ArrayList<>();

        int[] days = {100, 200, 300, 500, 1000};
        for (int d : days) {
            list.add(new Anniversary(d + "일", anniversary(start, d), d, false));
        }

        int[] years = {1, 2, 3, 5, 10};
        for (int y : years) {
            list.add(new Anniversary(y + "주년", yearAnniversary(start, y), y, true));
        }

        return list;
    }
}
