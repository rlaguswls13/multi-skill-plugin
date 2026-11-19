package com.msp.sqlseek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Multi-Column SQL Keyset / Seek Pagination Predicate & ORDER BY Builder for zero-offset deep pagination.
 * <p>
 * Supports:
 * <ul>
 *     <li>Multi-column sort specifications (e.g. {@code created_at DESC, priority ASC, id DESC})</li>
 *     <li>Expanded Disjunctive Form: {@code (a < ? OR (a = ? AND b > ?) OR (a = ? AND b = ? AND c < ?))}</li>
 *     <li>Row-Value Constructor Tuple Form: {@code (a, b) < (?, ?)} for uniform sort directions</li>
 *     <li>Forward ({@link NavigationDirection#NEXT_PAGE}) and Backward ({@link NavigationDirection#PREVIOUS_PAGE}) seek navigation</li>
 * </ul>
 */
public final class KeysetSeekBuilder {

    private final List<SortSpec> sortSpecs;
    private final PredicateStyle predicateStyle;
    private final NavigationDirection navigationDirection;
    private final List<Object> cursorValues;
    private final int pageSize;

    private KeysetSeekBuilder(
            List<SortSpec> sortSpecs,
            PredicateStyle predicateStyle,
            NavigationDirection navigationDirection,
            List<Object> cursorValues,
            int pageSize
    ) {
        if (sortSpecs == null || sortSpecs.isEmpty()) {
            throw new IllegalArgumentException("At least one SortSpec must be configured");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        this.sortSpecs = List.copyOf(sortSpecs);
        this.predicateStyle = Objects.requireNonNull(predicateStyle, "predicateStyle must not be null");
        this.navigationDirection = Objects.requireNonNull(navigationDirection, "navigationDirection must not be null");
        if (cursorValues != null && !cursorValues.isEmpty()) {
            if (cursorValues.size() != this.sortSpecs.size()) {
                throw new IllegalArgumentException(
                        "Cursor values count (" + cursorValues.size() + ") must match sortSpecs count (" + this.sortSpecs.size() + ")"
                );
            }
            for (int i = 0; i < cursorValues.size(); i++) {
                if (cursorValues.get(i) == null) {
                    throw new IllegalArgumentException("Cursor value at index " + i + " (" + this.sortSpecs.get(i).column() + ") must not be null");
                }
            }
            this.cursorValues = Collections.unmodifiableList(new ArrayList<>(cursorValues));
        } else {
            this.cursorValues = List.of();
        }
        this.pageSize = pageSize;
    }

    /**
     * Creates a new fluent builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds the {@link SeekQuerySegment} containing the WHERE predicate, ORDER BY clause, LIMIT clause, and bind parameters.
     */
    public SeekQuerySegment build() {
        List<SortSpec> effectiveSpecs = resolveEffectiveSortSpecs();
        String orderByClause = buildOrderByClause(effectiveSpecs);
        String limitClause = "LIMIT " + pageSize;

        if (cursorValues.isEmpty()) {
            return new SeekQuerySegment("", orderByClause, limitClause, List.of());
        }

        return switch (predicateStyle) {
            case EXPANDED_DISJUNCTIVE -> buildExpandedDisjunctive(effectiveSpecs, orderByClause, limitClause);
            case ROW_VALUE_TUPLE -> buildRowValueTuple(effectiveSpecs, orderByClause, limitClause);
        };
    }

    private List<SortSpec> resolveEffectiveSortSpecs() {
        if (navigationDirection == NavigationDirection.NEXT_PAGE) {
            return sortSpecs;
        }
        return sortSpecs.stream()
                .map(SortSpec::invertDirection)
                .toList();
    }

    private String buildOrderByClause(List<SortSpec> effectiveSpecs) {
        return "ORDER BY " + effectiveSpecs.stream()
                .map(spec -> spec.column() + " " + spec.direction().name())
                .collect(Collectors.joining(", "));
    }

    private SeekQuerySegment buildExpandedDisjunctive(
            List<SortSpec> effectiveSpecs,
            String orderByClause,
            String limitClause
    ) {
        int n = effectiveSpecs.size();
        List<Object> bindParams = new ArrayList<>(n * (n + 1) / 2);
        StringBuilder sb = new StringBuilder();
        sb.append('(');

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            if (i > 0) {
                sb.append('(');
            }
            for (int j = 0; j < i; j++) {
                sb.append(effectiveSpecs.get(j).column()).append(" = ? AND ");
                bindParams.add(cursorValues.get(j));
            }
            SortSpec current = effectiveSpecs.get(i);
            sb.append(current.column()).append(' ').append(current.direction().inequalityOperator()).append(" ?");
            bindParams.add(cursorValues.get(i));
            if (i > 0) {
                sb.append(')');
            }
        }

        sb.append(')');
        return new SeekQuerySegment(sb.toString(), orderByClause, limitClause, bindParams);
    }

    private SeekQuerySegment buildRowValueTuple(
            List<SortSpec> effectiveSpecs,
            String orderByClause,
            String limitClause
    ) {
        SortDirection firstDirection = effectiveSpecs.getFirst().direction();
        for (SortSpec spec : effectiveSpecs) {
            if (spec.direction() != firstDirection) {
                throw new IllegalStateException(
                        "ROW_VALUE_TUPLE predicate style requires uniform sort directions (all ASC or all DESC), "
                                + "but found mixed directions. Use EXPANDED_DISJUNCTIVE for mixed ASC/DESC."
                );
            }
        }

        String columnsTuple = effectiveSpecs.stream()
                .map(SortSpec::column)
                .collect(Collectors.joining(", ", "(", ")"));
        String placeholdersTuple = effectiveSpecs.stream()
                .map(s -> "?")
                .collect(Collectors.joining(", ", "(", ")"));

        String wherePredicate = columnsTuple + " " + firstDirection.inequalityOperator() + " " + placeholdersTuple;
        return new SeekQuerySegment(wherePredicate, orderByClause, limitClause, cursorValues);
    }

    /**
     * Fluent builder for {@link KeysetSeekBuilder}.
     */
    public static final class Builder {
        private final List<SortSpec> sortSpecs = new ArrayList<>();
        private PredicateStyle predicateStyle = PredicateStyle.EXPANDED_DISJUNCTIVE;
        private NavigationDirection navigationDirection = NavigationDirection.NEXT_PAGE;
        private List<Object> cursorValues = List.of();
        private int pageSize = 20;

        private Builder() {
        }

        public Builder addSort(String column, SortDirection direction) {
            this.sortSpecs.add(new SortSpec(column, direction));
            return this;
        }

        public Builder addSort(SortSpec sortSpec) {
            this.sortSpecs.add(Objects.requireNonNull(sortSpec, "sortSpec must not be null"));
            return this;
        }

        public Builder sortAsc(String column) {
            return addSort(SortSpec.asc(column));
        }

        public Builder sortDesc(String column) {
            return addSort(SortSpec.desc(column));
        }

        public Builder sortSpecs(List<SortSpec> specs) {
            Objects.requireNonNull(specs, "specs must not be null");
            this.sortSpecs.clear();
            this.sortSpecs.addAll(specs);
            return this;
        }

        public Builder predicateStyle(PredicateStyle predicateStyle) {
            this.predicateStyle = Objects.requireNonNull(predicateStyle, "predicateStyle must not be null");
            return this;
        }

        public Builder navigationDirection(NavigationDirection navigationDirection) {
            this.navigationDirection = Objects.requireNonNull(navigationDirection, "navigationDirection must not be null");
            return this;
        }

        public Builder cursorValues(List<?> cursorValues) {
            if (cursorValues == null) {
                this.cursorValues = List.of();
            } else {
                this.cursorValues = new ArrayList<>(cursorValues);
            }
            return this;
        }

        public Builder cursorValues(Object... values) {
            if (values == null || values.length == 0) {
                this.cursorValues = List.of();
            } else {
                List<Object> list = new ArrayList<>(values.length);
                for (Object v : values) {
                    if (v == null) {
                        throw new IllegalArgumentException("Cursor value must not be null");
                    }
                    list.add(v);
                }
                this.cursorValues = list;
            }
            return this;
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public SeekQuerySegment build() {
            return new KeysetSeekBuilder(
                    sortSpecs,
                    predicateStyle,
                    navigationDirection,
                    cursorValues,
                    pageSize
            ).build();
        }
    }
}
