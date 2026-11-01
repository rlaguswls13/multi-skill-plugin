# 🧩 skill-sql-parameter-binder-named

> Lexer-based Named Parameter SQL parser and binder converting named params (`:name`, `:orderIds`) into JDBC standard question mark (`?`) prepared statements with zero 3rd-party dependencies.

---

## 🌟 Key Features

- **Lexer-Based Parsing**: Character-by-character SQL tokenization without fragile regular expressions.
- **Literal & Comment Protection**: Safely ignores colons inside single-quoted strings (`'time is :00'`), single-line comments (`-- :ignored`), and block comments (`/* :ignored */`).
- **PostgreSQL Cast Support**: Safely distinguishes `::type` (PostgreSQL type casts) from named parameters.
- **Automatic Collection/Array Expansion**: Expands `:ids` bound to a `List`, `Set`, or array into JDBC-compliant comma-separated placeholders (`?, ?, ?`).
- **1-Based JDBC Indexing**: Generates `parameterIndexMap` mapping parameter names directly to `PreparedStatement.setObject(...)` positions.
- **Debug SQL Rendering**: `toDebugSql(...)` renders runnable SQL with literal-formatted and escaped values for logging.
- **Zero Dependencies**: Pure Java 21 standard library.

---

## 🚀 Quick Start

### 1. Basic Compilation and Parameter Mapping

```java
import com.msp.namedsql.NamedSqlBinder;
import com.msp.namedsql.ParsedNamedSql;
import java.util.Map;
import java.util.List;

Map<String, Object> params = Map.of(
    "name", "Alice",
    "status", "ACTIVE",
    "roles", List.of("ADMIN", "USER")
);

String namedSql = """
    SELECT u.id, u.name, u.created_at::date
    FROM users u
    WHERE u.name = :name
      AND u.status = :status
      AND u.role IN (:roles)
    """;

ParsedNamedSql result = NamedSqlBinder.compile(namedSql, params);

System.out.println(result.preparedSql());
// Output:
// SELECT u.id, u.name, u.created_at::date
// FROM users u
// WHERE u.name = ?
//   AND u.status = ?
//   AND u.role IN (?, ?)

System.out.println(result.parameterOrder());
// [name, status, roles, roles]

System.out.println(result.parameterIndexMap());
// {name=[1], status=[2], roles=[3, 4]}
```

### 2. Debug SQL Output for Logging

```java
List<Object> orderedValues = List.of("Alice", "ACTIVE", "ADMIN", "USER");
String debugSql = NamedSqlBinder.toDebugSql(result.preparedSql(), orderedValues);
System.out.println(debugSql);
```

---

## 🧪 Testing

```bash
.\gradlew.bat :modules:skill-sql-parameter-binder-named:test
```
