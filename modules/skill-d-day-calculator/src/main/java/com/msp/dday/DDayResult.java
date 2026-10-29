package com.msp.dday;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Result of a D-Day calculation between a target date and a base date.
 *
 * @param target   target event date
 * @param base     reference baseline date
 * @param daysDiff difference in days (positive: future, zero: today, negative: past)
 * @param label    formatted label ("D-Day", "D-1", "D-7", "D+1", "D+7")
 */
public record DDayResult(
        LocalDate target,
        LocalDate base,
        long daysDiff,
        String label
) {
    public DDayResult {
        Objects.requireNonNull(target, "target date must not be null");
        Objects.requireNonNull(base, "base date must not be null");
        Objects.requireNonNull(label, "label must not be null");
    }

    /**
     * True if the target date is identical to the baseline date.
     */
    public boolean isToday() {
        return daysDiff == 0;
    }

    /**
     * True if the target date has already passed relative to the baseline.
     */
    public boolean isPast() {
        return daysDiff < 0;
    }

    /**
     * True if the target date is in the future relative to the baseline.
     */
    public boolean isFuture() {
        return daysDiff > 0;
    }

    /**
     * Remaining days until target (positive integer, or 0 if today or past).
     */
    public long remainingDays() {
        return Math.max(0, daysDiff);
    }

    /**
     * Days elapsed since target (positive integer, or 0 if today or future).
     */
    public long elapsedDays() {
        return Math.max(0, -daysDiff);
    }

    /**
     * Returns a human-friendly Korean summary.
     */
    public String toKoreanSummary() {
        if (isToday()) {
            return "오늘 (D-Day)";
        } else if (isFuture()) {
            return remainingDays() + "일 남음 (" + label + ")";
        } else {
            return elapsedDays() + "일 지남 (" + label + ")";
        }
    }
}
