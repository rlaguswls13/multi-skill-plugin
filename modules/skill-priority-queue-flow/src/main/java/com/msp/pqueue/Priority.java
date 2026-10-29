package com.msp.pqueue;

/**
 * Priority levels from P0 (CRITICAL) down to P3 (LOW).
 */
public enum Priority {

    /** P0: Urgent tasks requiring immediate processing. Default quota weight: 8 */
    CRITICAL(0, 8),

    /** P1: High priority tasks. Default quota weight: 4 */
    HIGH(1, 4),

    /** P2: Standard priority tasks. Default quota weight: 2 */
    NORMAL(2, 2),

    /** P3: Low priority / background batch tasks. Default quota weight: 1 */
    LOW(3, 1);

    private final int level;
    private final int defaultWeight;

    Priority(int level, int defaultWeight) {
        this.level = level;
        this.defaultWeight = defaultWeight;
    }

    public int level() {
        return level;
    }

    public int defaultWeight() {
        return defaultWeight;
    }

    public static Priority fromLevel(int level) {
        return switch (level) {
            case 0 -> CRITICAL;
            case 1 -> HIGH;
            case 2 -> NORMAL;
            case 3 -> LOW;
            default -> throw new IllegalArgumentException("Unknown priority level: " + level + " (expected 0-3)");
        };
    }
}
