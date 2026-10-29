package com.msp.daterange;

/**
 * Defines boundary inclusion policies for date intervals.
 */
public enum InclusionPolicy {
    /**
     * Closed interval [start, end]: includes both start and end dates.
     */
    CLOSED(true, true, "[", "]"),

    /**
     * Half-open interval [start, end): includes start date, excludes end date (standard computer science convention).
     */
    HALF_OPEN(true, false, "[", ")"),

    /**
     * Open interval (start, end): excludes both start and end dates.
     */
    OPEN(false, false, "(", ")"),

    /**
     * Open-closed interval (start, end]: excludes start date, includes end date.
     */
    OPEN_CLOSED(false, true, "(", "]");

    private final boolean includesStart;
    private final boolean includesEnd;
    private final String openBracket;
    private final String closeBracket;

    InclusionPolicy(boolean includesStart, boolean includesEnd, String openBracket, String closeBracket) {
        this.includesStart = includesStart;
        this.includesEnd = includesEnd;
        this.openBracket = openBracket;
        this.closeBracket = closeBracket;
    }

    public boolean includesStart() {
        return includesStart;
    }

    public boolean includesEnd() {
        return includesEnd;
    }

    public String openBracket() {
        return openBracket;
    }

    public String closeBracket() {
        return closeBracket;
    }
}
