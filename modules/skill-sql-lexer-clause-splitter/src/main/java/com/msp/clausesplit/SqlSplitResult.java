package com.msp.clausesplit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable container for parsed SQL clauses and fluent modification operations.
 */
public final class SqlSplitResult {

    private final String originalSql;
    private final String leadingPrefix;
    private final List<SqlClause> clauses;

    public SqlSplitResult(String originalSql, String leadingPrefix, List<SqlClause> clauses) {
        this.originalSql = Objects.requireNonNull(originalSql, "originalSql cannot be null");
        this.leadingPrefix = leadingPrefix != null ? leadingPrefix : "";
        this.clauses = List.copyOf(Objects.requireNonNull(clauses, "clauses cannot be null"));
    }

    public String originalSql() {
        return originalSql;
    }

    public String leadingPrefix() {
        return leadingPrefix;
    }

    public List<SqlClause> clauses() {
        return clauses;
    }

    public boolean isEmpty() {
        return clauses.isEmpty();
    }

    public int size() {
        return clauses.size();
    }

    /**
     * Checks whether a clause of the given type exists.
     */
    public boolean hasClause(ClauseType type) {
        Objects.requireNonNull(type, "ClauseType cannot be null");
        return clauses.stream().anyMatch(c -> c.type() == type);
    }

    /**
     * Finds the first clause of the specified type.
     */
    public Optional<SqlClause> getClause(ClauseType type) {
        Objects.requireNonNull(type, "ClauseType cannot be null");
        return clauses.stream().filter(c -> c.type() == type).findFirst();
    }

    /**
     * Returns the trimmed body of the first clause of the specified type, if present.
     */
    public Optional<String> getClauseBody(ClauseType type) {
        return getClause(type).map(SqlClause::trimmedBody);
    }

    /**
     * Returns all clauses of the given type (e.g. for queries with UNIONs).
     */
    public List<SqlClause> getClauses(ClauseType type) {
        Objects.requireNonNull(type, "ClauseType cannot be null");
        return clauses.stream()
                .filter(c -> c.type() == type)
                .toList();
    }

    /**
     * Returns a new {@link SqlSplitResult} with the first occurrence of the specified clause type replaced.
     *
     * @param type    the clause type to replace
     * @param newBody the new body for the clause
     * @return a new SqlSplitResult
     * @throws IllegalArgumentException if no matching clause exists
     */
    public SqlSplitResult replaceClause(ClauseType type, String newBody) {
        Objects.requireNonNull(type, "ClauseType cannot be null");
        Objects.requireNonNull(newBody, "newBody cannot be null");

        int targetIndex = -1;
        for (int i = 0; i < clauses.size(); i++) {
            if (clauses.get(i).type() == type) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) {
            throw new IllegalArgumentException("Cannot replace clause: no clause of type " + type + " found");
        }

        List<SqlClause> updated = new ArrayList<>(clauses);
        SqlClause existing = updated.get(targetIndex);
        updated.set(targetIndex, new SqlClause(
                existing.type(),
                existing.keyword(),
                newBody.strip(),
                existing.startIndex(),
                existing.endIndex()
        ));

        return new SqlSplitResult(originalSql, leadingPrefix, updated);
    }

    /**
     * Appends a boolean condition to the WHERE clause using 'AND'.
     * If WHERE does not exist, a new WHERE clause is inserted in the logical position.
     */
    public SqlSplitResult appendConditionToWhere(String condition) {
        return appendConditionToWhere(condition, "AND");
    }

    /**
     * Appends a condition to the WHERE clause with the specified operator (e.g. 'AND', 'OR').
     * If no WHERE clause is currently present, creates and inserts one in the appropriate logical order.
     */
    public SqlSplitResult appendConditionToWhere(String condition, String operator) {
        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("Condition cannot be null or blank");
        }
        String op = (operator == null || operator.isBlank()) ? "AND" : operator.strip().toUpperCase();

        Optional<SqlClause> whereClause = getClause(ClauseType.WHERE);
        if (whereClause.isPresent()) {
            SqlClause existing = whereClause.get();
            String existingBody = existing.trimmedBody();
            String combinedBody = existingBody.isEmpty()
                    ? "(" + condition.strip() + ")"
                    : existingBody + " " + op + " (" + condition.strip() + ")";
            return replaceClause(ClauseType.WHERE, combinedBody);
        }

        // Insert new WHERE clause
        SqlClause newWhere = new SqlClause(ClauseType.WHERE, "WHERE", "(" + condition.strip() + ")", -1, -1);
        List<SqlClause> updated = new ArrayList<>(clauses);

        // Find insertion index: after FROM / UPDATE / DELETE_FROM / SET, or before GROUP_BY / HAVING / ORDER_BY / LIMIT / OFFSET
        int insertIndex = findWhereInsertionIndex(updated);
        updated.add(insertIndex, newWhere);

        return new SqlSplitResult(originalSql, leadingPrefix, updated);
    }

    private int findWhereInsertionIndex(List<SqlClause> list) {
        // Look for preceding clauses
        for (int i = list.size() - 1; i >= 0; i--) {
            ClauseType t = list.get(i).type();
            if (t == ClauseType.FROM || t == ClauseType.UPDATE || t == ClauseType.DELETE_FROM || t == ClauseType.SET) {
                return i + 1;
            }
        }
        // Look for succeeding clauses
        for (int i = 0; i < list.size(); i++) {
            ClauseType t = list.get(i).type();
            if (t == ClauseType.GROUP_BY || t == ClauseType.HAVING || t == ClauseType.ORDER_BY
                    || t == ClauseType.LIMIT || t == ClauseType.OFFSET
                    || t == ClauseType.UNION || t == ClauseType.UNION_ALL
                    || t == ClauseType.RETURNING) {
                return i;
            }
        }
        return list.size();
    }

    /**
     * Removes the first clause of the given type, if present.
     */
    public SqlSplitResult removeClause(ClauseType type) {
        Objects.requireNonNull(type, "ClauseType cannot be null");
        List<SqlClause> updated = new ArrayList<>();
        boolean removed = false;
        for (SqlClause clause : clauses) {
            if (!removed && clause.type() == type) {
                removed = true;
                continue;
            }
            updated.add(clause);
        }
        return new SqlSplitResult(originalSql, leadingPrefix, updated);
    }

    /**
     * Reconstructs the SQL statement, joining clauses with a newline character.
     */
    public String toSql() {
        return toSql("\n");
    }

    /**
     * Reconstructs the SQL statement, joining clauses with the specified delimiter.
     */
    public String toSql(String delimiter) {
        StringBuilder sb = new StringBuilder();
        if (!leadingPrefix.isBlank()) {
            sb.append(leadingPrefix.strip()).append("\n");
        }
        for (int i = 0; i < clauses.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(clauses.get(i).toSql());
        }
        return sb.toString();
    }

    /**
     * Reconstructs the SQL statement on a single line separated by single spaces.
     */
    public String toInlineSql() {
        return toSql(" ");
    }

    @Override
    public String toString() {
        return toSql();
    }
}
