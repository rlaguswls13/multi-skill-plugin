package com.msp.sqlwindow;

/**
 * Representation of a SQL window frame bound.
 */
public sealed interface FrameBound permits FrameBound.UnboundedPreceding, FrameBound.Preceding,
        FrameBound.CurrentRow, FrameBound.Following, FrameBound.UnboundedFollowing {

    String toSql();

    static FrameBound unboundedPreceding() {
        return UnboundedPreceding.INSTANCE;
    }

    static FrameBound preceding(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative: " + offset);
        }
        if (offset == 0) {
            return currentRow();
        }
        return new Preceding(offset);
    }

    static FrameBound currentRow() {
        return CurrentRow.INSTANCE;
    }

    static FrameBound following(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative: " + offset);
        }
        if (offset == 0) {
            return currentRow();
        }
        return new Following(offset);
    }

    static FrameBound unboundedFollowing() {
        return UnboundedFollowing.INSTANCE;
    }

    final class UnboundedPreceding implements FrameBound {
        static final UnboundedPreceding INSTANCE = new UnboundedPreceding();
        private UnboundedPreceding() {}

        @Override
        public String toSql() {
            return "UNBOUNDED PRECEDING";
        }

        @Override
        public String toString() {
            return toSql();
        }
    }

    record Preceding(long offset) implements FrameBound {
        public Preceding {
            if (offset <= 0) {
                throw new IllegalArgumentException("Preceding offset must be positive: " + offset);
            }
        }

        @Override
        public String toSql() {
            return offset + " PRECEDING";
        }

        @Override
        public String toString() {
            return toSql();
        }
    }

    final class CurrentRow implements FrameBound {
        static final CurrentRow INSTANCE = new CurrentRow();
        private CurrentRow() {}

        @Override
        public String toSql() {
            return "CURRENT ROW";
        }

        @Override
        public String toString() {
            return toSql();
        }
    }

    record Following(long offset) implements FrameBound {
        public Following {
            if (offset <= 0) {
                throw new IllegalArgumentException("Following offset must be positive: " + offset);
            }
        }

        @Override
        public String toSql() {
            return offset + " FOLLOWING";
        }

        @Override
        public String toString() {
            return toSql();
        }
    }

    final class UnboundedFollowing implements FrameBound {
        static final UnboundedFollowing INSTANCE = new UnboundedFollowing();
        private UnboundedFollowing() {}

        @Override
        public String toSql() {
            return "UNBOUNDED FOLLOWING";
        }

        @Override
        public String toString() {
            return toSql();
        }
    }
}
