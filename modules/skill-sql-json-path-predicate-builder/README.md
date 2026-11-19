# skill-sql-json-path-predicate-builder

Zero-dependency Java 21 multi-dialect SQL JSON path extractor and parameterized predicate builder for **PostgreSQL**, **MySQL**, and **SQLite**.

## Architecture Highlights
- **Multi-Dialect Support (`SqlJsonDialect`)**:
  - `POSTGRESQL`: Generates `->`, `->>`, `#>`, `#>>`, `@>`, `?`, and `jsonb_typeof` expressions.
  - `MYSQL`: Generates `JSON_EXTRACT`, `JSON_UNQUOTE`, `JSON_CONTAINS`, `JSON_CONTAINS_PATH`, and `JSON_TYPE`.
  - `SQLITE`: Generates `json_extract` and `json_type`.
- **SQL Injection Defense (`SqlIdentifierValidator`)**:
  - Strictly validates SQL column identifiers (`col`, `table.col`, `schema.table.col`) and JSON path segments (`JsonPathSegment.Property` / `JsonPathSegment.ArrayIndex`).
  - Always binds runtime values via positional JDBC `?` placeholders inside immutable `SqlFragment(sql, parameters)` records.
- **Composable Predicates**:
  - Fluent `and`, `or`, `not`, `andAll`, `orAll` combinators that preserve exact bind parameter ordering.

## Quick Start

```java
import com.msp.sqljson.*;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        SqlJsonPredicateBuilder pg = SqlJsonPredicateBuilder.forDialect(SqlJsonDialect.POSTGRESQL);

        SqlFragment where = pg.eq("orders.payload", JsonPath.parse("$.customer.tier"), "GOLD")
                .and(pg.gte("orders.payload", JsonPath.parse("$.items[0].price"), 500))
                .and(pg.containsJson("orders.payload", "{\"express\":true}"));

        System.out.println("SQL: " + where.sql());
        System.out.println("Params: " + where.parameters());
    }
}
```
