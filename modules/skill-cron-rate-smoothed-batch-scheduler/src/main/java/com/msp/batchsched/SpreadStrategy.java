package com.msp.batchsched;

/**
 * Ordering and anti-hotspot distribution strategy applied before slicing items into time slots.
 */
public enum SpreadStrategy {

    /** Preserves the exact input order. */
    PRESERVE_ORDER,

    /** Schedules higher-priority items in earlier slots (stable within equal priority). */
    PRIORITY_DESC,

    /** Interleaves items sharing the same {@link BatchItem#key()} round-robin across slots to avoid hot-spotting. */
    ROUND_ROBIN_BY_KEY,

    /** Orders by priority descending, and interleaves by key within each priority tier. */
    PRIORITY_THEN_KEY_SPREAD
}
