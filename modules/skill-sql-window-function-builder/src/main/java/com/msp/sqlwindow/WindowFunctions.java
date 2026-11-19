package com.msp.sqlwindow;

import java.util.List;
import java.util.Objects;

/**
 * Factory class for SQL window functions and helper builders.
 */
public final class WindowFunctions {

    private WindowFunctions() {}

    // --- Ranking Functions ---

    public static WindowFunctionCall rowNumber() {
        return WindowFunctionCall.of("ROW_NUMBER");
    }

    public static WindowFunctionCall rank() {
        return WindowFunctionCall.of("RANK");
    }

    public static WindowFunctionCall denseRank() {
        return WindowFunctionCall.of("DENSE_RANK");
    }

    public static WindowFunctionCall percentRank() {
        return WindowFunctionCall.of("PERCENT_RANK");
    }

    public static WindowFunctionCall cumeDist() {
        return WindowFunctionCall.of("CUME_DIST");
    }

    public static WindowFunctionCall ntile(int buckets) {
        if (buckets <= 0) {
            throw new IllegalArgumentException("NTILE bucket count must be positive: " + buckets);
        }
        return WindowFunctionCall.of("NTILE", String.valueOf(buckets));
    }

    // --- Value / Offset Functions ---

    public static WindowFunctionCall lead(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("LEAD", column);
    }

    public static WindowFunctionCall lead(String column, int offset) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("LEAD", column, String.valueOf(offset));
    }

    public static WindowFunctionCall lead(String column, int offset, Object defaultValue) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("LEAD", column, String.valueOf(offset), formatDefaultValue(defaultValue));
    }

    public static WindowFunctionCall lag(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("LAG", column);
    }

    public static WindowFunctionCall lag(String column, int offset) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("LAG", column, String.valueOf(offset));
    }

    public static WindowFunctionCall lag(String column, int offset, Object defaultValue) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("LAG", column, String.valueOf(offset), formatDefaultValue(defaultValue));
    }

    public static WindowFunctionCall firstValue(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("FIRST_VALUE", column);
    }

    public static WindowFunctionCall lastValue(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("LAST_VALUE", column);
    }

    public static WindowFunctionCall nthValue(String column, int n) {
        Objects.requireNonNull(column, "column cannot be null");
        if (n <= 0) {
            throw new IllegalArgumentException("NTH_VALUE index must be positive: " + n);
        }
        return WindowFunctionCall.of("NTH_VALUE", column, String.valueOf(n));
    }

    // --- Aggregate Functions ---

    public static WindowFunctionCall sum(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("SUM", column);
    }

    public static WindowFunctionCall avg(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("AVG", column);
    }

    public static WindowFunctionCall count(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("COUNT", column);
    }

    public static WindowFunctionCall count() {
        return WindowFunctionCall.of("COUNT", "*");
    }

    public static WindowFunctionCall min(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("MIN", column);
    }

    public static WindowFunctionCall max(String column) {
        Objects.requireNonNull(column, "column cannot be null");
        return WindowFunctionCall.of("MAX", column);
    }

    public static WindowFunctionCall custom(String functionName, String... args) {
        return WindowFunctionCall.of(functionName, args);
    }

    // --- Helper Helpers ---

    public static OrderSpec asc(String column) {
        return OrderSpec.asc(column);
    }

    public static OrderSpec desc(String column) {
        return OrderSpec.desc(column);
    }

    public static FrameBound unboundedPreceding() {
        return FrameBound.unboundedPreceding();
    }

    public static FrameBound preceding(long n) {
        return FrameBound.preceding(n);
    }

    public static FrameBound currentRow() {
        return FrameBound.currentRow();
    }

    public static FrameBound following(long n) {
        return FrameBound.following(n);
    }

    public static FrameBound unboundedFollowing() {
        return FrameBound.unboundedFollowing();
    }

    private static String formatDefaultValue(Object val) {
        if (val == null) {
            return "NULL";
        }
        if (val instanceof Number || val instanceof Boolean) {
            return val.toString();
        }
        String str = val.toString();
        if (str.startsWith("'") && str.endsWith("'")) {
            return str;
        }
        return "'" + str.replace("'", "''") + "'";
    }
}
