package com.msp.namedsql;

import java.lang.reflect.Array;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance lexer-based Named Parameter SQL parser and binder.
 * Converts named SQL (:param, :list) into standard JDBC prepared SQL with '?' placeholders,
 * safely expanding collections/arrays and protecting string literals, comments, and PostgreSQL casts.
 */
public final class NamedSqlBinder {

    private NamedSqlBinder() {
        // Utility class
    }

    /**
     * Compiles a SQL string containing named parameters into a {@link ParsedNamedSql}.
     *
     * @param namedSql   the SQL statement with named parameters (e.g., ":name", ":ids")
     * @param parameters map of parameter names to their bound values
     * @return the compiled {@link ParsedNamedSql}
     * @throws IllegalArgumentException if a required parameter is missing or a collection parameter is empty
     */
    public static ParsedNamedSql compile(String namedSql, Map<String, Object> parameters) {
        Objects.requireNonNull(namedSql, "namedSql must not be null");
        Map<String, Object> params = parameters != null ? parameters : Map.of();

        StringBuilder sb = new StringBuilder(namedSql.length() + 32);
        List<String> parameterOrder = new ArrayList<>();
        Map<String, List<Integer>> parameterIndexMap = new LinkedHashMap<>();

        int length = namedSql.length();
        int i = 0;
        int placeholderIndex = 1;

        while (i < length) {
            char c = namedSql.charAt(i);

            // 1. Single quote string literal: '...'
            if (c == '\'') {
                sb.append(c);
                i++;
                while (i < length) {
                    char sc = namedSql.charAt(i);
                    sb.append(sc);
                    if (sc == '\'') {
                        // Check for escaped single quote: ''
                        if (i + 1 < length && namedSql.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }

            // 2. Single-line comment: --
            if (c == '-' && i + 1 < length && namedSql.charAt(i + 1) == '-') {
                sb.append("--");
                i += 2;
                while (i < length) {
                    char cc = namedSql.charAt(i);
                    sb.append(cc);
                    i++;
                    if (cc == '\n') {
                        break;
                    }
                }
                continue;
            }

            // 3. Block comment: /* ... */
            if (c == '/' && i + 1 < length && namedSql.charAt(i + 1) == '*') {
                sb.append("/*");
                i += 2;
                while (i < length) {
                    char cc = namedSql.charAt(i);
                    sb.append(cc);
                    if (cc == '*' && i + 1 < length && namedSql.charAt(i + 1) == '/') {
                        sb.append('/');
                        i += 2;
                        break;
                    }
                    i++;
                }
                continue;
            }

            // 4. Colon check: could be ::cast or :paramName or lone :
            if (c == ':') {
                // Check for PostgreSQL type cast '::'
                if (i + 1 < length && namedSql.charAt(i + 1) == ':') {
                    sb.append("::");
                    i += 2;
                    continue;
                }

                // Check if followed by valid identifier start
                if (i + 1 < length && isIdentifierStart(namedSql.charAt(i + 1))) {
                    int start = i + 1;
                    int end = start;
                    while (end < length && isIdentifierPart(namedSql.charAt(end))) {
                        end++;
                    }
                    String paramName = namedSql.substring(start, end);
                    i = end;

                    if (!params.containsKey(paramName)) {
                        throw new IllegalArgumentException("Unsupplied parameter: '" + paramName + "' in SQL");
                    }

                    Object val = params.get(paramName);
                    int count = getElementCount(paramName, val);

                    for (int k = 0; k < count; k++) {
                        if (k > 0) {
                            sb.append(", ");
                        }
                        sb.append('?');
                        parameterOrder.add(paramName);
                        parameterIndexMap.computeIfAbsent(paramName, k2 -> new ArrayList<>())
                                .add(placeholderIndex++);
                    }
                    continue;
                } else {
                    sb.append(':');
                    i++;
                    continue;
                }
            }

            // Standard characters
            sb.append(c);
            i++;
        }

        return new ParsedNamedSql(sb.toString(), parameterOrder, parameterIndexMap);
    }

    /**
     * Renders a debug SQL statement by replacing '?' placeholders with formatted literal representations
     * of the given parameter values.
     *
     * @param preparedSql   the SQL statement with '?' placeholders
     * @param orderedValues the list of parameter values corresponding in order to each '?'
     * @return the SQL string with parameter values embedded for debugging
     */
    public static String toDebugSql(String preparedSql, List<Object> orderedValues) {
        Objects.requireNonNull(preparedSql, "preparedSql must not be null");
        List<Object> values = orderedValues != null ? orderedValues : List.of();

        StringBuilder sb = new StringBuilder(preparedSql.length() + 64);
        int length = preparedSql.length();
        int i = 0;
        int valueIndex = 0;

        while (i < length) {
            char c = preparedSql.charAt(i);

            // 1. Single quote string literal
            if (c == '\'') {
                sb.append(c);
                i++;
                while (i < length) {
                    char sc = preparedSql.charAt(i);
                    sb.append(sc);
                    if (sc == '\'') {
                        if (i + 1 < length && preparedSql.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }

            // 2. Single-line comment: --
            if (c == '-' && i + 1 < length && preparedSql.charAt(i + 1) == '-') {
                sb.append("--");
                i += 2;
                while (i < length) {
                    char cc = preparedSql.charAt(i);
                    sb.append(cc);
                    i++;
                    if (cc == '\n') {
                        break;
                    }
                }
                continue;
            }

            // 3. Block comment: /* ... */
            if (c == '/' && i + 1 < length && preparedSql.charAt(i + 1) == '*') {
                sb.append("/*");
                i += 2;
                while (i < length) {
                    char cc = preparedSql.charAt(i);
                    sb.append(cc);
                    if (cc == '*' && i + 1 < length && preparedSql.charAt(i + 1) == '/') {
                        sb.append('/');
                        i += 2;
                        break;
                    }
                    i++;
                }
                continue;
            }

            // 4. Placeholder '?'
            if (c == '?') {
                if (valueIndex >= values.size()) {
                    throw new IllegalArgumentException("Not enough parameter values supplied for SQL placeholders: expected at least "
                            + (valueIndex + 1) + ", but got " + values.size());
                }
                Object val = values.get(valueIndex++);
                sb.append(formatSqlLiteral(val));
                i++;
                continue;
            }

            sb.append(c);
            i++;
        }

        if (valueIndex < values.size()) {
            throw new IllegalArgumentException("Too many parameter values supplied: expected "
                    + valueIndex + ", but got " + values.size());
        }

        return sb.toString();
    }

    private static int getElementCount(String paramName, Object val) {
        if (val == null) {
            return 1;
        }
        if (val instanceof Collection<?> coll) {
            if (coll.isEmpty()) {
                throw new IllegalArgumentException("Collection parameter '" + paramName + "' cannot be empty.");
            }
            return coll.size();
        }
        if (val.getClass().isArray()) {
            int len = Array.getLength(val);
            if (len == 0) {
                throw new IllegalArgumentException("Array parameter '" + paramName + "' cannot be empty.");
            }
            return len;
        }
        return 1;
    }

    private static String formatSqlLiteral(Object val) {
        if (val == null) {
            return "NULL";
        }
        if (val instanceof Number) {
            return val.toString();
        }
        if (val instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }
        if (val instanceof Temporal || val instanceof Date || val instanceof Calendar) {
            return "'" + val + "'";
        }
        String str = val.toString();
        return "'" + str.replace("'", "''") + "'";
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }
}
