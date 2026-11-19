package com.msp.sqlupsert;

import java.util.*;

/**
 * Fluent builder for generating multi-dialect SQL UPSERT statements.
 */
public final class UpsertBuilder {

    private final String table;
    private final List<String> conflictKeys = new ArrayList<>();
    private final LinkedHashMap<String, Object> columnValues = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> customUpdateExpressions = new LinkedHashMap<>();
    private final Set<String> updateColumns = new LinkedHashSet<>();
    private boolean doNothingOnConflict = false;
    private DatabaseDialect dialect = DatabaseDialect.POSTGRESQL;
    private ParameterStyle parameterStyle = ParameterStyle.POSITIONAL;

    private UpsertBuilder(String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        this.table = table.trim();
    }

    /**
     * Creates a new builder for the specified table.
     */
    public static UpsertBuilder into(String table) {
        return new UpsertBuilder(table);
    }

    /**
     * Sets primary/unique conflict target keys.
     */
    public UpsertBuilder conflictKeys(String... keys) {
        if (keys != null) {
            for (String key : keys) {
                if (key != null && !key.isBlank()) {
                    this.conflictKeys.add(key.trim());
                }
            }
        }
        return this;
    }

    /**
     * Sets primary/unique conflict target keys from a collection.
     */
    public UpsertBuilder conflictKeys(Collection<String> keys) {
        if (keys != null) {
            for (String key : keys) {
                if (key != null && !key.isBlank()) {
                    this.conflictKeys.add(key.trim());
                }
            }
        }
        return this;
    }

    /**
     * Adds a column and its bound value.
     */
    public UpsertBuilder value(String column, Object value) {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be null or blank");
        }
        this.columnValues.put(column.trim(), value);
        return this;
    }

    /**
     * Adds multiple column-value pairs.
     */
    public UpsertBuilder values(Map<String, ?> values) {
        if (values != null) {
            values.forEach(this::value);
        }
        return this;
    }

    /**
     * Specifies which columns should be updated if a conflict occurs.
     */
    public UpsertBuilder updateOnConflict(String... columns) {
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.isBlank()) {
                    this.updateColumns.add(col.trim());
                }
            }
        }
        return this;
    }

    /**
     * Specifies that all value columns except the conflict keys and any explicitly excluded columns
     * should be updated on conflict.
     */
    public UpsertBuilder updateOnConflictAllExcept(String... excludeColumns) {
        Set<String> excludes = new HashSet<>(this.conflictKeys);
        if (excludeColumns != null) {
            for (String ex : excludeColumns) {
                if (ex != null) excludes.add(ex.trim());
            }
        }
        for (String col : this.columnValues.keySet()) {
            if (!excludes.contains(col)) {
                this.updateColumns.add(col);
            }
        }
        return this;
    }

    /**
     * Adds a custom SQL expression for a column on conflict update (e.g. "updated_at", "NOW()").
     */
    public UpsertBuilder updateCustom(String column, String sqlExpression) {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be null or blank");
        }
        if (sqlExpression == null || sqlExpression.isBlank()) {
            throw new IllegalArgumentException("SQL expression cannot be null or blank");
        }
        String colName = column.trim();
        this.customUpdateExpressions.put(colName, sqlExpression.trim());
        this.updateColumns.add(colName);
        return this;
    }

    /**
     * Instructs generator to perform DO NOTHING / IGNORE on conflict instead of updating.
     */
    public UpsertBuilder doNothingOnConflict() {
        this.doNothingOnConflict = true;
        return this;
    }

    /**
     * Configures the target database SQL dialect.
     */
    public UpsertBuilder forDialect(DatabaseDialect dialect) {
        if (dialect == null) {
            throw new IllegalArgumentException("Database dialect cannot be null");
        }
        this.dialect = dialect;
        return this;
    }

    /**
     * Configures parameter placeholder style.
     */
    public UpsertBuilder parameterStyle(ParameterStyle parameterStyle) {
        if (parameterStyle == null) {
            throw new IllegalArgumentException("Parameter style cannot be null");
        }
        this.parameterStyle = parameterStyle;
        return this;
    }

    /**
     * Builds the complete UpsertStatement for the configured dialect.
     */
    public UpsertStatement build() {
        if (columnValues.isEmpty()) {
            throw new IllegalStateException("At least one column and value must be provided for UPSERT");
        }

        if (!doNothingOnConflict && updateColumns.isEmpty()) {
            // Default behavior: update all columns except conflict keys
            updateOnConflictAllExcept();
        }

        if (conflictKeys.isEmpty() && !isDialectPermittingEmptyConflictKeys(dialect)) {
            throw new IllegalStateException("Conflict key(s) must be specified for " + dialect);
        }

        String sql = switch (dialect) {
            case POSTGRESQL -> buildPostgreSql();
            case SQLITE -> buildSqlite();
            case MYSQL, MARIADB -> buildMySql();
            case ORACLE -> buildOracle();
            case H2 -> buildH2();
            case SQL_SERVER -> buildSqlServer();
        };

        List<Object> paramList = new ArrayList<>(columnValues.values());
        Map<String, Object> paramMap = Collections.unmodifiableMap(new LinkedHashMap<>(columnValues));

        return new UpsertStatement(sql, paramList, paramMap, dialect, table);
    }

    private boolean isDialectPermittingEmptyConflictKeys(DatabaseDialect d) {
        return d == DatabaseDialect.MYSQL || d == DatabaseDialect.MARIADB;
    }

    private String buildPostgreSql() {
        StringBuilder sb = new StringBuilder();
        List<String> cols = new ArrayList<>(columnValues.keySet());

        sb.append("INSERT INTO ").append(table).append(" (");
        sb.append(String.join(", ", cols)).append(")\nVALUES (");
        appendPlaceholders(sb, cols);
        sb.append(")");

        if (doNothingOnConflict) {
            sb.append("\nON CONFLICT (").append(String.join(", ", conflictKeys)).append(") DO NOTHING");
        } else {
            sb.append("\nON CONFLICT (").append(String.join(", ", conflictKeys)).append(") DO UPDATE SET\n  ");
            List<String> setClauses = new ArrayList<>();
            for (String col : updateColumns) {
                if (customUpdateExpressions.containsKey(col)) {
                    setClauses.add(col + " = " + customUpdateExpressions.get(col));
                } else {
                    setClauses.add(col + " = EXCLUDED." + col);
                }
            }
            sb.append(String.join(",\n  ", setClauses));
        }
        return sb.toString();
    }

    private String buildSqlite() {
        StringBuilder sb = new StringBuilder();
        List<String> cols = new ArrayList<>(columnValues.keySet());

        sb.append("INSERT INTO ").append(table).append(" (");
        sb.append(String.join(", ", cols)).append(")\nVALUES (");
        appendPlaceholders(sb, cols);
        sb.append(")");

        if (doNothingOnConflict) {
            sb.append("\nON CONFLICT (").append(String.join(", ", conflictKeys)).append(") DO NOTHING");
        } else {
            sb.append("\nON CONFLICT (").append(String.join(", ", conflictKeys)).append(") DO UPDATE SET\n  ");
            List<String> setClauses = new ArrayList<>();
            for (String col : updateColumns) {
                if (customUpdateExpressions.containsKey(col)) {
                    setClauses.add(col + " = " + customUpdateExpressions.get(col));
                } else {
                    setClauses.add(col + " = excluded." + col);
                }
            }
            sb.append(String.join(",\n  ", setClauses));
        }
        return sb.toString();
    }

    private String buildMySql() {
        StringBuilder sb = new StringBuilder();
        List<String> cols = new ArrayList<>(columnValues.keySet());

        if (doNothingOnConflict) {
            sb.append("INSERT IGNORE INTO ").append(table).append(" (");
            sb.append(String.join(", ", cols)).append(")\nVALUES (");
            appendPlaceholders(sb, cols);
            sb.append(")");
            return sb.toString();
        }

        sb.append("INSERT INTO ").append(table).append(" (");
        sb.append(String.join(", ", cols)).append(")\nVALUES (");
        appendPlaceholders(sb, cols);
        sb.append(")\nON DUPLICATE KEY UPDATE\n  ");

        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            if (customUpdateExpressions.containsKey(col)) {
                setClauses.add(col + " = " + customUpdateExpressions.get(col));
            } else {
                setClauses.add(col + " = VALUES(" + col + ")");
            }
        }
        sb.append(String.join(",\n  ", setClauses));
        return sb.toString();
    }

    private String buildOracle() {
        StringBuilder sb = new StringBuilder();
        List<String> cols = new ArrayList<>(columnValues.keySet());

        sb.append("MERGE INTO ").append(table).append(" t\nUSING (SELECT ");
        List<String> selectCols = new ArrayList<>();
        for (String col : cols) {
            selectCols.add(parameterStyle.placeholder(col) + " AS " + col);
        }
        sb.append(String.join(", ", selectCols)).append(" FROM DUAL) s\nON (");

        List<String> onClauses = new ArrayList<>();
        for (String key : conflictKeys) {
            onClauses.add("t." + key + " = s." + key);
        }
        sb.append(String.join(" AND ", onClauses)).append(")");

        if (!doNothingOnConflict && !updateColumns.isEmpty()) {
            sb.append("\nWHEN MATCHED THEN\n  UPDATE SET\n    ");
            List<String> updateClauses = new ArrayList<>();
            for (String col : updateColumns) {
                if (customUpdateExpressions.containsKey(col)) {
                    updateClauses.add("t." + col + " = " + customUpdateExpressions.get(col));
                } else {
                    updateClauses.add("t." + col + " = s." + col);
                }
            }
            sb.append(String.join(",\n    ", updateClauses));
        }

        sb.append("\nWHEN NOT MATCHED THEN\n  INSERT (");
        sb.append(String.join(", ", cols)).append(")\n  VALUES (");
        List<String> sourceCols = cols.stream().map(c -> "s." + c).toList();
        sb.append(String.join(", ", sourceCols)).append(")");

        return sb.toString();
    }

    private String buildH2() {
        // H2 supports standard SQL MERGE INTO target USING (SELECT ... FROM DUAL)
        return buildOracle();
    }

    private String buildSqlServer() {
        StringBuilder sb = new StringBuilder();
        List<String> cols = new ArrayList<>(columnValues.keySet());

        sb.append("MERGE INTO ").append(table).append(" AS target\nUSING (VALUES (");
        appendPlaceholders(sb, cols);
        sb.append(")) AS source (").append(String.join(", ", cols)).append(")\nON (");

        List<String> onClauses = new ArrayList<>();
        for (String key : conflictKeys) {
            onClauses.add("target." + key + " = source." + key);
        }
        sb.append(String.join(" AND ", onClauses)).append(")");

        if (!doNothingOnConflict && !updateColumns.isEmpty()) {
            sb.append("\nWHEN MATCHED THEN\n  UPDATE SET\n    ");
            List<String> updateClauses = new ArrayList<>();
            for (String col : updateColumns) {
                if (customUpdateExpressions.containsKey(col)) {
                    updateClauses.add("target." + col + " = " + customUpdateExpressions.get(col));
                } else {
                    updateClauses.add("target." + col + " = source." + col);
                }
            }
            sb.append(String.join(",\n    ", updateClauses));
        }

        sb.append("\nWHEN NOT MATCHED THEN\n  INSERT (");
        sb.append(String.join(", ", cols)).append(")\n  VALUES (");
        List<String> sourceCols = cols.stream().map(c -> "source." + c).toList();
        sb.append(String.join(", ", sourceCols)).append(");");

        return sb.toString();
    }

    private void appendPlaceholders(StringBuilder sb, List<String> cols) {
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parameterStyle.placeholder(cols.get(i)));
        }
    }
}
