package com.msp.ddl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * SQL CREATE TABLE DDL generator from Java classes and records using reflection.
 * Pure Java 21 implementation supporting PostgreSQL, MySQL, and H2 dialects.
 */
public final class TableDdlGenerator {

    private TableDdlGenerator() {
    }

    /**
     * Generates a CREATE TABLE DDL statement for the specified class using the given dialect.
     *
     * @param entityClass target Java class or record
     * @param dialect     SQL dialect (POSTGRESQL, MYSQL, H2)
     * @return formatted SQL CREATE TABLE DDL
     */
    public static String generateDdl(Class<?> entityClass, SqlDialect dialect) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        Objects.requireNonNull(dialect, "dialect must not be null");

        String tableName = toSnakeCase(entityClass.getSimpleName());
        List<FieldMeta> fields = extractFields(entityClass);

        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Class " + entityClass.getName() + " has no persistent fields or record components");
        }

        // Determine primary key: prefer exact "id", else first field ending with "id"
        FieldMeta primaryKeyField = findPrimaryKey(fields);

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(tableName).append(" (\n");

        for (int i = 0; i < fields.size(); i++) {
            FieldMeta field = fields.get(i);
            String colName = toSnakeCase(field.name);
            String colType = resolveSqlType(field.type, field.name, dialect);
            boolean isPk = (primaryKeyField != null && primaryKeyField == field);

            sql.append("    ").append(colName).append(" ").append(colType);
            if (isPk) {
                sql.append(" PRIMARY KEY");
            }
            if (i < fields.size() - 1) {
                sql.append(",");
            }
            sql.append("\n");
        }

        sql.append(");");
        return sql.toString();
    }

    /**
     * Generates a CREATE TABLE DDL statement using PostgreSQL dialect as default.
     */
    public static String generateDdl(Class<?> entityClass) {
        return generateDdl(entityClass, SqlDialect.POSTGRESQL);
    }

    /**
     * Converts camelCase or PascalCase string to snake_case.
     *
     * @param input source string
     * @return snake_cased string
     */
    public static String toSnakeCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                if (i > 0 && chars[i - 1] != '_' && (!Character.isUpperCase(chars[i - 1]) || (i + 1 < chars.length && Character.isLowerCase(chars[i + 1])))) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<FieldMeta> extractFields(Class<?> clazz) {
        List<FieldMeta> list = new ArrayList<>();
        if (clazz.isRecord()) {
            for (RecordComponent rc : clazz.getRecordComponents()) {
                list.add(new FieldMeta(rc.getName(), rc.getType()));
            }
        } else {
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                        continue;
                    }
                    list.add(new FieldMeta(f.getName(), f.getType()));
                }
                current = current.getSuperclass();
            }
        }
        return list;
    }

    private static FieldMeta findPrimaryKey(List<FieldMeta> fields) {
        for (FieldMeta f : fields) {
            if (f.name.equalsIgnoreCase("id")) {
                return f;
            }
        }
        for (FieldMeta f : fields) {
            String lower = f.name.toLowerCase(Locale.ROOT);
            if (lower.endsWith("id") && !lower.equals("valid") && !lower.equals("grid")) {
                return f;
            }
        }
        return null;
    }

    private static String resolveSqlType(Class<?> type, String fieldName, SqlDialect dialect) {
        String lowerName = fieldName.toLowerCase(Locale.ROOT);

        if (type == String.class || type == CharSequence.class) {
            if (lowerName.endsWith("content") || lowerName.endsWith("text") || lowerName.endsWith("description")) {
                return "TEXT";
            }
            return "VARCHAR(255)";
        }
        if (type == int.class || type == Integer.class) {
            return "INTEGER";
        }
        if (type == long.class || type == Long.class) {
            return "BIGINT";
        }
        if (type == boolean.class || type == Boolean.class) {
            return (dialect == SqlDialect.MYSQL) ? "TINYINT(1)" : "BOOLEAN";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "DOUBLE PRECISION";
        }
        if (type == BigDecimal.class) {
            return "DECIMAL(19, 4)";
        }
        if (type == Instant.class || type == LocalDateTime.class) {
            return "TIMESTAMP";
        }
        if (type == LocalDate.class) {
            return "DATE";
        }
        if (type == UUID.class) {
            return (dialect == SqlDialect.MYSQL) ? "VARCHAR(36)" : "UUID";
        }
        if (type == short.class || type == Short.class) {
            return "SMALLINT";
        }
        if (type == byte.class || type == Byte.class) {
            return "TINYINT";
        }

        return "VARCHAR(255)";
    }

    private record FieldMeta(String name, Class<?> type) {
    }
}
