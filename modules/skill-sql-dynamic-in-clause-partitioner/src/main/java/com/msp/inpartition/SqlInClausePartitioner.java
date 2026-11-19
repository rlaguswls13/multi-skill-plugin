package com.msp.inpartition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dynamic SQL IN-clause partitioner and query generator.
 * <p>
 * Splits large parameter collections (e.g. 5,000+ IDs) into chunks of size {@code K}
 * (e.g. 500 or 1000) to bypass database limits (such as Oracle's 1000-item IN limit
 * or SQLite's 999 parameter limit), generating safe, parameterized SQL clauses.
 */
public final class SqlInClausePartitioner {

    /**
     * Default chunk size safe across major databases (Oracle, PostgreSQL, MySQL).
     */
    public static final int DEFAULT_CHUNK_SIZE = 1000;

    private SqlInClausePartitioner() {
        // utility class
    }

    /**
     * Partitions values into positional '?' IN-clauses using the default chunk size (1000).
     *
     * @param column column name
     * @param values collection of parameter values
     * @return partitioned SQL clause and flattened parameters
     */
    public static SqlInClause partition(String column, Collection<?> values) {
        return partition(column, values, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Partitions values into positional '?' IN-clauses with a specified chunk size.
     *
     * @param column    column name
     * @param values    collection of parameter values
     * @param chunkSize maximum number of items per chunk (must be >= 1)
     * @return partitioned SQL clause and flattened parameters
     */
    public static SqlInClause partition(String column, Collection<?> values, int chunkSize) {
        validateColumn(column);
        validateChunkSize(chunkSize);

        if (values == null || values.isEmpty()) {
            return SqlInClause.empty();
        }

        List<Object> list = new ArrayList<>(values);
        int total = list.size();
        int chunkCount = (total + chunkSize - 1) / chunkSize;

        if (chunkCount == 1) {
            StringBuilder sb = new StringBuilder();
            sb.append(column).append(" IN (");
            appendPlaceholders(sb, total);
            sb.append(")");
            return new SqlInClause(sb.toString(), list, 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int c = 0; c < chunkCount; c++) {
            if (c > 0) {
                sb.append(" OR ");
            }
            int start = c * chunkSize;
            int end = Math.min(start + chunkSize, total);
            int countInChunk = end - start;

            sb.append(column).append(" IN (");
            appendPlaceholders(sb, countInChunk);
            sb.append(")");
        }
        sb.append(")");

        return new SqlInClause(sb.toString(), list, chunkCount);
    }

    /**
     * Partitions values into named parameter (:param) IN-clauses using default chunk size (1000).
     *
     * @param column      column name
     * @param paramPrefix parameter name prefix (e.g. "id")
     * @param values      collection of parameter values
     * @return partitioned named SQL clause and parameter map
     */
    public static NamedSqlInClause partitionNamed(String column, String paramPrefix, Collection<?> values) {
        return partitionNamed(column, paramPrefix, values, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Partitions values into named parameter (:param) IN-clauses with specified chunk size.
     * Generates SQL like {@code (col IN (:id_0_0, :id_0_1) OR col IN (:id_1_0))} with flattened map.
     *
     * @param column      column name
     * @param paramPrefix parameter name prefix (e.g. "id")
     * @param values      collection of parameter values
     * @param chunkSize   maximum number of items per chunk (must be >= 1)
     * @return partitioned named SQL clause and parameter map
     */
    public static NamedSqlInClause partitionNamed(String column, String paramPrefix, Collection<?> values, int chunkSize) {
        validateColumn(column);
        validateParamPrefix(paramPrefix);
        validateChunkSize(chunkSize);

        if (values == null || values.isEmpty()) {
            return NamedSqlInClause.empty();
        }

        String prefix = cleanParamPrefix(paramPrefix);
        List<Object> list = new ArrayList<>(values);
        int total = list.size();
        int chunkCount = (total + chunkSize - 1) / chunkSize;
        Map<String, Object> paramMap = new LinkedHashMap<>();

        if (chunkCount == 1) {
            StringBuilder sb = new StringBuilder();
            sb.append(column).append(" IN (");
            for (int i = 0; i < total; i++) {
                if (i > 0) sb.append(", ");
                String paramName = prefix + "_0_" + i;
                sb.append(":").append(paramName);
                paramMap.put(paramName, list.get(i));
            }
            sb.append(")");
            return new NamedSqlInClause(sb.toString(), paramMap, 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int c = 0; c < chunkCount; c++) {
            if (c > 0) {
                sb.append(" OR ");
            }
            int start = c * chunkSize;
            int end = Math.min(start + chunkSize, total);

            sb.append(column).append(" IN (");
            for (int i = start; i < end; i++) {
                if (i > start) sb.append(", ");
                int itemIndex = i - start;
                String paramName = prefix + "_" + c + "_" + itemIndex;
                sb.append(":").append(paramName);
                paramMap.put(paramName, list.get(i));
            }
            sb.append(")");
        }
        sb.append(")");

        return new NamedSqlInClause(sb.toString(), paramMap, chunkCount);
    }

    /**
     * Partitions composite keys using SQL tuple syntax {@code (col1, col2) IN ((?, ?), ...)}.
     *
     * @param columns list of composite column names
     * @param keys    collection of composite keys
     * @return partitioned SQL clause and flattened parameters
     */
    public static SqlInClause partitionComposite(List<String> columns, Collection<? extends CompositeKey> keys) {
        return partitionComposite(columns, keys, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Partitions composite keys using SQL tuple syntax {@code (col1, col2) IN ((?, ?), ...)}
     * with specified chunk size.
     *
     * @param columns   list of composite column names
     * @param keys      collection of composite keys
     * @param chunkSize maximum number of composite keys per chunk
     * @return partitioned SQL clause and flattened parameters
     */
    public static SqlInClause partitionComposite(List<String> columns, Collection<? extends CompositeKey> keys, int chunkSize) {
        validateColumns(columns);
        validateChunkSize(chunkSize);

        if (keys == null || keys.isEmpty()) {
            return SqlInClause.empty();
        }

        int colCount = columns.size();
        List<CompositeKey> keyList = new ArrayList<>(keys);
        validateCompositeKeys(keyList, colCount);

        int total = keyList.size();
        int chunkCount = (total + chunkSize - 1) / chunkSize;
        List<Object> flattenedParams = new ArrayList<>(total * colCount);

        String colTuple = "(" + String.join(", ", columns) + ")";

        if (chunkCount == 1) {
            StringBuilder sb = new StringBuilder();
            sb.append(colTuple).append(" IN (");
            appendCompositePlaceholders(sb, total, colCount);
            sb.append(")");

            for (CompositeKey key : keyList) {
                flattenedParams.addAll(key.values());
            }
            return new SqlInClause(sb.toString(), flattenedParams, 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int c = 0; c < chunkCount; c++) {
            if (c > 0) {
                sb.append(" OR ");
            }
            int start = c * chunkSize;
            int end = Math.min(start + chunkSize, total);
            int countInChunk = end - start;

            sb.append(colTuple).append(" IN (");
            appendCompositePlaceholders(sb, countInChunk, colCount);
            sb.append(")");

            for (int i = start; i < end; i++) {
                flattenedParams.addAll(keyList.get(i).values());
            }
        }
        sb.append(")");

        return new SqlInClause(sb.toString(), flattenedParams, chunkCount);
    }

    /**
     * Partitions composite keys using disjunctive fallback syntax {@code ((col1 = ? AND col2 = ?) OR ...)}
     * for databases that do not support standard tuple IN syntax (e.g. certain older DB versions or SQL dialects).
     *
     * @param columns   list of composite column names
     * @param keys      collection of composite keys
     * @param chunkSize maximum number of composite keys per chunk
     * @return partitioned SQL clause and flattened parameters
     */
    public static SqlInClause partitionCompositeDisjunctive(List<String> columns, Collection<? extends CompositeKey> keys, int chunkSize) {
        validateColumns(columns);
        validateChunkSize(chunkSize);

        if (keys == null || keys.isEmpty()) {
            return SqlInClause.empty();
        }

        int colCount = columns.size();
        List<CompositeKey> keyList = new ArrayList<>(keys);
        validateCompositeKeys(keyList, colCount);

        int total = keyList.size();
        int chunkCount = (total + chunkSize - 1) / chunkSize;
        List<Object> flattenedParams = new ArrayList<>(total * colCount);

        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int c = 0; c < chunkCount; c++) {
            if (c > 0) {
                sb.append(" OR ");
            }
            int start = c * chunkSize;
            int end = Math.min(start + chunkSize, total);

            if (chunkCount > 1) {
                sb.append("(");
            }
            for (int i = start; i < end; i++) {
                if (i > start) {
                    sb.append(" OR ");
                }
                sb.append("(");
                for (int colIdx = 0; colIdx < colCount; colIdx++) {
                    if (colIdx > 0) sb.append(" AND ");
                    sb.append(columns.get(colIdx)).append(" = ?");
                }
                sb.append(")");
                flattenedParams.addAll(keyList.get(i).values());
            }
            if (chunkCount > 1) {
                sb.append(")");
            }
        }
        sb.append(")");

        return new SqlInClause(sb.toString(), flattenedParams, chunkCount);
    }

    private static void appendPlaceholders(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
    }

    private static void appendCompositePlaceholders(StringBuilder sb, int keyCount, int colCount) {
        for (int i = 0; i < keyCount; i++) {
            if (i > 0) sb.append(", ");
            sb.append("(");
            for (int c = 0; c < colCount; c++) {
                if (c > 0) sb.append(", ");
                sb.append("?");
            }
            sb.append(")");
        }
    }

    private static void validateColumn(String column) {
        Objects.requireNonNull(column, "column name must not be null");
        if (column.isBlank()) {
            throw new IllegalArgumentException("column name must not be blank");
        }
    }

    private static void validateColumns(List<String> columns) {
        Objects.requireNonNull(columns, "columns must not be null");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        for (String col : columns) {
            validateColumn(col);
        }
    }

    private static void validateChunkSize(int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize must be >= 1, got: " + chunkSize);
        }
    }

    private static void validateParamPrefix(String prefix) {
        Objects.requireNonNull(prefix, "paramPrefix must not be null");
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("paramPrefix must not be blank");
        }
    }

    private static String cleanParamPrefix(String prefix) {
        String p = prefix.trim();
        while (p.startsWith(":")) {
            p = p.substring(1);
        }
        if (p.isEmpty()) {
            throw new IllegalArgumentException("paramPrefix must contain valid identifier characters");
        }
        return p;
    }

    private static void validateCompositeKeys(List<CompositeKey> keys, int expectedSize) {
        for (int i = 0; i < keys.size(); i++) {
            CompositeKey k = keys.get(i);
            if (k == null) {
                throw new IllegalArgumentException("CompositeKey at index " + i + " is null");
            }
            if (k.size() != expectedSize) {
                throw new IllegalArgumentException("CompositeKey at index " + i + " has size " + k.size()
                        + " but expected " + expectedSize + " columns");
            }
        }
    }
}
