package com.msp.sqlwindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable specification of a window:
 * PARTITION BY ... ORDER BY ... ROWS/RANGE/GROUPS BETWEEN ...
 * or a reference to a named window.
 */
public record WindowSpec(
        String namedWindow,
        List<String> partitionColumns,
        List<OrderSpec> orderSpecs,
        FrameClause frameClause
) {

    public WindowSpec {
        partitionColumns = partitionColumns == null ? List.of() : List.copyOf(partitionColumns);
        orderSpecs = orderSpecs == null ? List.of() : List.copyOf(orderSpecs);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WindowSpec empty() {
        return new WindowSpec(null, List.of(), List.of(), null);
    }

    public static WindowSpec named(String name) {
        Objects.requireNonNull(name, "named window cannot be null");
        return new WindowSpec(name, List.of(), List.of(), null);
    }

    public Optional<String> getNamedWindow() {
        return Optional.ofNullable(namedWindow);
    }

    public Optional<FrameClause> getFrameClause() {
        return Optional.ofNullable(frameClause);
    }

    public String toSql() {
        if (namedWindow != null && !namedWindow.isBlank()) {
            return namedWindow;
        }

        List<String> parts = new ArrayList<>();
        if (!partitionColumns.isEmpty()) {
            parts.add("PARTITION BY " + String.join(", ", partitionColumns));
        }
        if (!orderSpecs.isEmpty()) {
            StringBuilder sb = new StringBuilder("ORDER BY ");
            for (int i = 0; i < orderSpecs.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(orderSpecs.get(i).toSql());
            }
            parts.add(sb.toString());
        }
        if (frameClause != null) {
            parts.add(frameClause.toSql());
        }

        return String.join(" ", parts);
    }

    public static final class Builder {
        private String namedWindow;
        private final List<String> partitionColumns = new ArrayList<>();
        private final List<OrderSpec> orderSpecs = new ArrayList<>();
        private FrameClause frameClause;

        public Builder named(String name) {
            this.namedWindow = name;
            return this;
        }

        public Builder partitionBy(String... columns) {
            if (columns != null) {
                for (String col : columns) {
                    if (col != null && !col.isBlank()) {
                        this.partitionColumns.add(col.trim());
                    }
                }
            }
            return this;
        }

        public Builder partitionBy(List<String> columns) {
            if (columns != null) {
                for (String col : columns) {
                    if (col != null && !col.isBlank()) {
                        this.partitionColumns.add(col.trim());
                    }
                }
            }
            return this;
        }

        public Builder orderBy(OrderSpec... specs) {
            if (specs != null) {
                for (OrderSpec spec : specs) {
                    if (spec != null) {
                        this.orderSpecs.add(spec);
                    }
                }
            }
            return this;
        }

        public Builder orderBy(String column) {
            return orderBy(OrderSpec.asc(column));
        }

        public Builder orderBy(String column, SortDirection direction) {
            return orderBy(new OrderSpec(column, direction, NullsOrder.NONE));
        }

        public Builder orderBy(String column, SortDirection direction, NullsOrder nullsOrder) {
            return orderBy(new OrderSpec(column, direction, nullsOrder));
        }

        public Builder orderByDesc(String column) {
            return orderBy(OrderSpec.desc(column));
        }

        public Builder frame(FrameClause frameClause) {
            this.frameClause = frameClause;
            return this;
        }

        public Builder rowsBetween(FrameBound start, FrameBound end) {
            this.frameClause = FrameClause.rows(start, end);
            return this;
        }

        public Builder rowsBetween(FrameBound start, FrameBound end, FrameExclusion exclusion) {
            this.frameClause = FrameClause.rows(start, end, exclusion);
            return this;
        }

        public Builder rangeBetween(FrameBound start, FrameBound end) {
            this.frameClause = FrameClause.range(start, end);
            return this;
        }

        public Builder rangeBetween(FrameBound start, FrameBound end, FrameExclusion exclusion) {
            this.frameClause = FrameClause.range(start, end, exclusion);
            return this;
        }

        public Builder groupsBetween(FrameBound start, FrameBound end) {
            this.frameClause = FrameClause.groups(start, end);
            return this;
        }

        public Builder groupsBetween(FrameBound start, FrameBound end, FrameExclusion exclusion) {
            this.frameClause = FrameClause.groups(start, end, exclusion);
            return this;
        }

        public WindowSpec build() {
            return new WindowSpec(namedWindow, partitionColumns, orderSpecs, frameClause);
        }
    }
}
