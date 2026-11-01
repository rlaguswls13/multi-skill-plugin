package com.msp.cronbiz;

/**
 * Roll policy defining how scheduled cron executions should be adjusted when falling on a non-business day
 * (e.g. weekend or public holiday).
 */
public enum BusinessDayRollPolicy {

    /**
     * Rolls execution forward to the next immediately available business day.
     */
    ROLL_FORWARD,

    /**
     * Rolls execution backward to the closest preceding business day.
     */
    ROLL_BACKWARD,

    /**
     * Skips / cancels execution for this occurrence.
     */
    SKIP
}
