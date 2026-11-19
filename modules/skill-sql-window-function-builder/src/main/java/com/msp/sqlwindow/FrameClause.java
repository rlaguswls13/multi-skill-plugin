package com.msp.sqlwindow;

import java.util.Objects;

/**
 * SQL Window Frame specification:
 * e.g., ROWS BETWEEN 1 PRECEDING AND CURRENT ROW EXCLUDE TIES
 */
public record FrameClause(FrameType type, FrameBound start, FrameBound end, FrameExclusion exclusion) {

    public FrameClause {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(start, "start bound cannot be null");
        Objects.requireNonNull(end, "end bound cannot be null");
        if (exclusion == null) {
            exclusion = FrameExclusion.NONE;
        }
    }

    public static FrameClause rows(FrameBound start, FrameBound end) {
        return new FrameClause(FrameType.ROWS, start, end, FrameExclusion.NONE);
    }

    public static FrameClause rows(FrameBound start, FrameBound end, FrameExclusion exclusion) {
        return new FrameClause(FrameType.ROWS, start, end, exclusion);
    }

    public static FrameClause range(FrameBound start, FrameBound end) {
        return new FrameClause(FrameType.RANGE, start, end, FrameExclusion.NONE);
    }

    public static FrameClause range(FrameBound start, FrameBound end, FrameExclusion exclusion) {
        return new FrameClause(FrameType.RANGE, start, end, exclusion);
    }

    public static FrameClause groups(FrameBound start, FrameBound end) {
        return new FrameClause(FrameType.GROUPS, start, end, FrameExclusion.NONE);
    }

    public static FrameClause groups(FrameBound start, FrameBound end, FrameExclusion exclusion) {
        return new FrameClause(FrameType.GROUPS, start, end, exclusion);
    }

    public String toSql() {
        return type.name() + " BETWEEN " + start.toSql() + " AND " + end.toSql() + exclusion.toSql();
    }
}
