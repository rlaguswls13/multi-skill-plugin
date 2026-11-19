package com.msp.sqljson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe, multi-dialect SQL JSON path extractor and parameterized predicate builder.
 *
 * <p>Generates dialect-accurate SQL expressions and bind-parameterized predicates for:
 * <ul>
 *   <li>{@link SqlJsonDialect#POSTGRESQL}: {@code ->}, {@code ->>}, {@code #>}, {@code #>>}, {@code @>}, {@code ?}, {@code jsonb_typeof}</li>
 *   <li>{@link SqlJsonDialect#MYSQL}: {@code JSON_EXTRACT}, {@code JSON_UNQUOTE}, {@code JSON_CONTAINS}, {@code JSON_CONTAINS_PATH}, {@code JSON_TYPE}</li>
 *   <li>{@link SqlJsonDialect#SQLITE}: {@code json_extract}, {@code json_type}</li>
 * </ul>
 *
 * <p>All SQL column identifiers and JSON path segments are strictly validated against SQL injection,
 * while runtime comparison values are always emitted as JDBC {@code ?} bind parameters.
 */
public final class SqlJsonPredicateBuilder {

    private final SqlJsonDialect dialect;

    private SqlJsonPredicateBuilder(SqlJsonDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
    }

    /**
     * Returns a builder instance for the requested SQL dialect.
     */
    public static SqlJsonPredicateBuilder forDialect(SqlJsonDialect dialect) {
        return new SqlJsonPredicateBuilder(dialect);
    }

    public SqlJsonDialect dialect() {
        return dialect;
    }

    /**
     * Generates a dialect-specific JSON extraction expression returning a JSON value.
     */
    public String extractJson(String column, JsonPath path) {
        String col = SqlIdentifierValidator.validateColumn(column);
        Objects.requireNonNull(path, "path must not be null");
        if (path.isEmpty()) {
            return switch (dialect) {
                case POSTGRESQL -> col;
                case MYSQL -> "JSON_EXTRACT(" + col + ", '$')";
                case SQLITE -> "json_extract(" + col + ", '$')";
            };
        }
        return switch (dialect) {
            case POSTGRESQL -> {
                if (path.size() == 1) {
                    yield switch (path.segments().getFirst()) {
                        case JsonPathSegment.Property p -> col + " -> '" + p.name() + "'";
                        case JsonPathSegment.ArrayIndex idx -> col + " -> " + idx.index();
                    };
                }
                yield col + " #> " + path.toPostgresPathArrayLiteral();
            }
            case MYSQL -> "JSON_EXTRACT(" + col + ", '" + path.toDollarPath() + "')";
            case SQLITE -> "json_extract(" + col + ", '" + path.toDollarPath() + "')";
        };
    }

    /**
     * Generates a dialect-specific extraction expression returning an unquoted text/scalar value.
     */
    public String extractText(String column, JsonPath path) {
        String col = SqlIdentifierValidator.validateColumn(column);
        Objects.requireNonNull(path, "path must not be null");
        if (path.isEmpty()) {
            return switch (dialect) {
                case POSTGRESQL -> col + " #>> '{}'";
                case MYSQL -> "JSON_UNQUOTE(JSON_EXTRACT(" + col + ", '$'))";
                case SQLITE -> "json_extract(" + col + ", '$')";
            };
        }
        return switch (dialect) {
            case POSTGRESQL -> {
                if (path.size() == 1) {
                    yield switch (path.segments().getFirst()) {
                        case JsonPathSegment.Property p -> col + " ->> '" + p.name() + "'";
                        case JsonPathSegment.ArrayIndex idx -> col + " ->> " + idx.index();
                    };
                }
                yield col + " #>> " + path.toPostgresPathArrayLiteral();
            }
            case MYSQL -> "JSON_UNQUOTE(JSON_EXTRACT(" + col + ", '" + path.toDollarPath() + "'))";
            case SQLITE -> "json_extract(" + col + ", '" + path.toDollarPath() + "')";
        };
    }

    /**
     * Builds an equality predicate ({@code = ?}). If {@code value} is {@code null}, delegates to {@link #isNull}.
     */
    public SqlFragment eq(String column, JsonPath path, Object value) {
        if (value == null) {
            return isNull(column, path);
        }
        return comparison(column, path, "=", value);
    }

    /**
     * Builds an inequality predicate ({@code <> ?}). If {@code value} is {@code null}, delegates to {@link #isNotNull}.
     */
    public SqlFragment ne(String column, JsonPath path, Object value) {
        if (value == null) {
            return isNotNull(column, path);
        }
        return comparison(column, path, "<>", value);
    }

    /**
     * Builds a greater-than predicate ({@code > ?}).
     */
    public SqlFragment gt(String column, JsonPath path, Object value) {
        Objects.requireNonNull(value, "value must not be null for '>' comparison");
        return comparison(column, path, ">", value);
    }

    /**
     * Builds a greater-than-or-equal predicate ({@code >= ?}).
     */
    public SqlFragment gte(String column, JsonPath path, Object value) {
        Objects.requireNonNull(value, "value must not be null for '>=' comparison");
        return comparison(column, path, ">=", value);
    }

    /**
     * Builds a less-than predicate ({@code < ?}).
     */
    public SqlFragment lt(String column, JsonPath path, Object value) {
        Objects.requireNonNull(value, "value must not be null for '<' comparison");
        return comparison(column, path, "<", value);
    }

    /**
     * Builds a less-than-or-equal predicate ({@code <= ?}).
     */
    public SqlFragment lte(String column, JsonPath path, Object value) {
        Objects.requireNonNull(value, "value must not be null for '<=' comparison");
        return comparison(column, path, "<=", value);
    }

    /**
     * Builds a {@code LIKE ?} predicate against the extracted text value.
     */
    public SqlFragment like(String column, JsonPath path, String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        String expr = extractText(column, path);
        return SqlFragment.of(expr + " LIKE ?", pattern);
    }

    /**
     * Builds an {@code IN (?, ?, ...)} predicate against the extracted value.
     */
    public SqlFragment in(String column, JsonPath path, Collection<?> values) {
        Objects.requireNonNull(values, "values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("IN predicate requires at least one value");
        }
        List<Object> params = new ArrayList<>(values.size());
        for (Object v : values) {
            if (v == null) {
                throw new IllegalArgumentException("IN predicate values must not contain null");
            }
            params.add(v);
        }
        String lhs = typedExtractionExpression(column, path, params.getFirst());
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append('?');
        }
        return new SqlFragment(lhs + " IN (" + placeholders + ")", params);
    }

    /**
     * Builds a predicate checking that the value at {@code path} is missing or JSON {@code null}.
     */
    public SqlFragment isNull(String column, JsonPath path) {
        String col = SqlIdentifierValidator.validateColumn(column);
        Objects.requireNonNull(path, "path must not be null");
        return switch (dialect) {
            case POSTGRESQL -> SqlFragment.of("(" + extractText(col, path) + ") IS NULL");
            case MYSQL -> {
                String extracted = extractJson(col, path);
                yield SqlFragment.of("(" + extracted + " IS NULL OR JSON_TYPE(" + extracted + ") = 'NULL')");
            }
            case SQLITE -> {
                String typeExpr = "json_type(" + col + ", '" + path.toDollarPath() + "')";
                yield SqlFragment.of("(" + typeExpr + " IS NULL OR " + typeExpr + " = 'null')");
            }
        };
    }

    /**
     * Builds a predicate checking that the value at {@code path} exists and is not JSON {@code null}.
     */
    public SqlFragment isNotNull(String column, JsonPath path) {
        String col = SqlIdentifierValidator.validateColumn(column);
        Objects.requireNonNull(path, "path must not be null");
        return switch (dialect) {
            case POSTGRESQL -> SqlFragment.of("(" + extractText(col, path) + ") IS NOT NULL");
            case MYSQL -> {
                String extracted = extractJson(col, path);
                yield SqlFragment.of("(" + extracted + " IS NOT NULL AND JSON_TYPE(" + extracted + ") <> 'NULL')");
            }
            case SQLITE -> {
                String typeExpr = "json_type(" + col + ", '" + path.toDollarPath() + "')";
                yield SqlFragment.of("(" + typeExpr + " IS NOT NULL AND " + typeExpr + " <> 'null')");
            }
        };
    }

    /**
     * Builds a predicate checking whether a top-level key exists in the JSON object.
     *
     * <p>Uses PostgreSQL {@code ?} operator, MySQL {@code JSON_CONTAINS_PATH}, and SQLite {@code json_type}.
     */
    public SqlFragment hasKey(String column, String key) {
        String col = SqlIdentifierValidator.validateColumn(column);
        String validatedKey = SqlIdentifierValidator.validateJsonProperty(key);
        return switch (dialect) {
            case POSTGRESQL -> SqlFragment.of(col + " ? ?", validatedKey);
            case MYSQL -> SqlFragment.of("JSON_CONTAINS_PATH(" + col + ", 'one', '$." + validatedKey + "') = 1");
            case SQLITE -> SqlFragment.of("json_type(" + col + ", '$." + validatedKey + "') IS NOT NULL");
        };
    }

    /**
     * Builds a predicate checking whether the given JSON path exists (even if its value is JSON {@code null}).
     */
    public SqlFragment pathExists(String column, JsonPath path) {
        String col = SqlIdentifierValidator.validateColumn(column);
        Objects.requireNonNull(path, "path must not be null");
        if (path.isEmpty()) {
            return switch (dialect) {
                case POSTGRESQL -> SqlFragment.of(col + " IS NOT NULL");
                case MYSQL -> SqlFragment.of("JSON_CONTAINS_PATH(" + col + ", 'one', '$') = 1");
                case SQLITE -> SqlFragment.of("json_type(" + col + ", '$') IS NOT NULL");
            };
        }
        return switch (dialect) {
            case POSTGRESQL -> {
                if (path.size() == 1 && path.segments().getFirst() instanceof JsonPathSegment.Property p) {
                    yield SqlFragment.of(col + " ? ?", p.name());
                }
                yield SqlFragment.of("(" + extractJson(col, path) + ") IS NOT NULL");
            }
            case MYSQL -> SqlFragment.of("JSON_CONTAINS_PATH(" + col + ", 'one', '" + path.toDollarPath() + "') = 1");
            case SQLITE -> SqlFragment.of("json_type(" + col + ", '" + path.toDollarPath() + "') IS NOT NULL");
        };
    }

    /**
     * Builds a JSON containment predicate at the root of {@code column}.
     *
     * <p>Uses PostgreSQL {@code @>}, MySQL {@code JSON_CONTAINS}, and SQLite {@code json_extract/json}.
     */
    public SqlFragment containsJson(String column, String jsonLiteral) {
        return containsJson(column, JsonPath.root(), jsonLiteral);
    }

    /**
     * Builds a JSON containment predicate at the specified {@code path}.
     */
    public SqlFragment containsJson(String column, JsonPath path, String jsonLiteral) {
        String col = SqlIdentifierValidator.validateColumn(column);
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(jsonLiteral, "jsonLiteral must not be null");
        return switch (dialect) {
            case POSTGRESQL -> {
                String target = path.isEmpty() ? col : "(" + extractJson(col, path) + ")";
                yield SqlFragment.of(target + " @> CAST(? AS jsonb)", jsonLiteral);
            }
            case MYSQL -> {
                if (path.isEmpty()) {
                    yield SqlFragment.of("JSON_CONTAINS(" + col + ", CAST(? AS JSON)) = 1", jsonLiteral);
                }
                yield SqlFragment.of("JSON_CONTAINS(" + col + ", CAST(? AS JSON), '" + path.toDollarPath() + "') = 1", jsonLiteral);
            }
            case SQLITE -> {
                String dollar = path.toDollarPath();
                yield SqlFragment.of("json_type(" + col + ", '" + dollar + "') IS NOT NULL AND json_extract(" + col + ", '" + dollar + "') = json(?)", jsonLiteral);
            }
        };
    }

    /**
     * Builds a predicate verifying the runtime JSON value type at {@code path}.
     */
    public SqlFragment hasType(String column, JsonPath path, JsonValueType type) {
        String col = SqlIdentifierValidator.validateColumn(column);
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(type, "type must not be null");
        return switch (dialect) {
            case POSTGRESQL -> {
                String target = extractJson(col, path);
                String pgType = switch (type) {
                    case OBJECT -> "object";
                    case ARRAY -> "array";
                    case STRING -> "string";
                    case NUMBER -> "number";
                    case BOOLEAN -> "boolean";
                    case NULL -> "null";
                };
                yield SqlFragment.of("jsonb_typeof(" + target + ") = ?", pgType);
            }
            case MYSQL -> {
                String target = extractJson(col, path);
                if (type == JsonValueType.NUMBER) {
                    yield SqlFragment.of("JSON_TYPE(" + target + ") IN ('INTEGER', 'DOUBLE', 'DECIMAL')");
                }
                String mysqlType = switch (type) {
                    case OBJECT -> "OBJECT";
                    case ARRAY -> "ARRAY";
                    case STRING -> "STRING";
                    case BOOLEAN -> "BOOLEAN";
                    case NULL -> "NULL";
                    case NUMBER -> throw new AssertionError();
                };
                yield SqlFragment.of("JSON_TYPE(" + target + ") = ?", mysqlType);
            }
            case SQLITE -> {
                String typeExpr = "json_type(" + col + ", '" + path.toDollarPath() + "')";
                yield switch (type) {
                    case OBJECT -> SqlFragment.of(typeExpr + " = ?", "object");
                    case ARRAY -> SqlFragment.of(typeExpr + " = ?", "array");
                    case STRING -> SqlFragment.of(typeExpr + " = ?", "text");
                    case NUMBER -> SqlFragment.of(typeExpr + " IN ('integer', 'real')");
                    case BOOLEAN -> SqlFragment.of(typeExpr + " IN ('true', 'false')");
                    case NULL -> SqlFragment.of(typeExpr + " = ?", "null");
                };
            }
        };
    }

    private SqlFragment comparison(String column, JsonPath path, String operator, Object value) {
        String lhs = typedExtractionExpression(column, path, value);
        return SqlFragment.of(lhs + " " + operator + " ?", value);
    }

    private String typedExtractionExpression(String column, JsonPath path, Object sampleValue) {
        if (sampleValue instanceof Number) {
            return switch (dialect) {
                case POSTGRESQL -> "CAST((" + extractText(column, path) + ") AS NUMERIC)";
                case MYSQL -> extractJson(column, path);
                case SQLITE -> extractJson(column, path);
            };
        }
        if (sampleValue instanceof Boolean) {
            return switch (dialect) {
                case POSTGRESQL -> "CAST((" + extractText(column, path) + ") AS BOOLEAN)";
                case MYSQL -> extractJson(column, path);
                case SQLITE -> extractJson(column, path);
            };
        }
        return extractText(column, path);
    }
}
