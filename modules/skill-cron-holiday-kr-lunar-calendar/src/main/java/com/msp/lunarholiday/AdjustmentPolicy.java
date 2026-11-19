package com.msp.lunarholiday;

/**
 * Adjustment policy applied when a scheduled job or cron execution falls on a Korean holiday or weekend.
 */
public enum AdjustmentPolicy {
    /**
     * Roll forward to the next business day (skipping weekend and holidays).
     */
    ROLL_FORWARD,

    /**
     * Roll backward to the preceding business day.
     */
    ROLL_BACKWARD,

    /**
     * Skip execution on holidays/weekends (returns null).
     */
    SKIP
}
