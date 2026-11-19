package com.msp.sqlcte;

import java.util.Objects;
import java.util.Optional;

/**
 * Cycle detection configuration for recursive CTE graph/tree traversal.
 */
public sealed interface CycleDetectionConfig {

    /**
     * No cycle detection.
     */
    record None() implements CycleDetectionConfig {}

    /**
     * ANSI SQL:1999/2016 standard CYCLE clause:
     * e.g., CYCLE id SET is_cycle TO 'Y' DEFAULT 'N' USING path
     */
    record AnsiCycleClause(
            String cycleColumn,
            String flagColumn,
            String flagTrueValue,
            String flagDefaultValue,
            String pathColumn
    ) implements CycleDetectionConfig {
        public AnsiCycleClause {
            Objects.requireNonNull(cycleColumn, "cycleColumn cannot be null");
            Objects.requireNonNull(flagColumn, "flagColumn cannot be null");
            flagTrueValue = flagTrueValue == null ? "'Y'" : flagTrueValue;
            flagDefaultValue = flagDefaultValue == null ? "'N'" : flagDefaultValue;
            Objects.requireNonNull(pathColumn, "pathColumn cannot be null");
        }

        public String toSqlClause() {
            return "CYCLE " + cycleColumn + " SET " + flagColumn +
                    " TO " + flagTrueValue + " DEFAULT " + flagDefaultValue +
                    " USING " + pathColumn;
        }
    }

    /**
     * Cycle detection by string path containment check in WHERE clause:
     * e.g., INSTR(path, '/' || id || '/') = 0 or POSITION('/' || CAST(id AS VARCHAR) || '/' IN path) = 0
     */
    record PathContainment(
            String pathColumn,
            String idColumn,
            String delimiter,
            PathCheckStyle checkStyle
    ) implements CycleDetectionConfig {
        public enum PathCheckStyle {
            POSITION, // POSITION(needle IN haystack) = 0
            INSTR,    // INSTR(haystack, needle) = 0
            NOT_LIKE  // path NOT LIKE '%/' || id || '/%'
        }

        public PathContainment {
            Objects.requireNonNull(pathColumn, "pathColumn cannot be null");
            Objects.requireNonNull(idColumn, "idColumn cannot be null");
            delimiter = delimiter == null ? "/" : delimiter;
            checkStyle = checkStyle == null ? PathCheckStyle.POSITION : checkStyle;
        }

        public String toWhereClause() {
            String needle = "'" + delimiter + "' || CAST(" + idColumn + " AS VARCHAR(50)) || '" + delimiter + "'";
            return switch (checkStyle) {
                case POSITION -> "POSITION(" + needle + " IN " + pathColumn + ") = 0";
                case INSTR -> "INSTR(" + pathColumn + ", " + needle + ") = 0";
                case NOT_LIKE -> pathColumn + " NOT LIKE ('%' || " + needle + " || '%')";
            };
        }
    }

    /**
     * Cycle detection by depth limit check in WHERE clause:
     * e.g., depth < maxDepth
     */
    record MaxDepth(String depthColumn, int maxDepth) implements CycleDetectionConfig {
        public MaxDepth {
            Objects.requireNonNull(depthColumn, "depthColumn cannot be null");
            if (maxDepth <= 0) {
                throw new IllegalArgumentException("maxDepth must be greater than 0");
            }
        }

        public String toWhereClause() {
            return depthColumn + " < " + maxDepth;
        }
    }

    static CycleDetectionConfig none() {
        return new None();
    }

    static CycleDetectionConfig ansiCycle(String cycleColumn, String flagColumn, String pathColumn) {
        return new AnsiCycleClause(cycleColumn, flagColumn, "'Y'", "'N'", pathColumn);
    }

    static CycleDetectionConfig pathContains(String pathColumn, String idColumn) {
        return new PathContainment(pathColumn, idColumn, "/", PathContainment.PathCheckStyle.POSITION);
    }

    static CycleDetectionConfig pathContains(String pathColumn, String idColumn, String delimiter, PathContainment.PathCheckStyle style) {
        return new PathContainment(pathColumn, idColumn, delimiter, style);
    }

    static CycleDetectionConfig maxDepth(String depthColumn, int maxDepth) {
        return new MaxDepth(depthColumn, maxDepth);
    }
}
