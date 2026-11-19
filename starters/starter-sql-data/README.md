# 🗄️ starter-sql-data

> **SQL Builder, Identifier Sanitizer, SQL Injection Detector, Keyset Seek Pagination, Sharding Router & JSON Path Predicate Starter Bundle**

Zero-dependency Java 21 domain starter aggregating 6 single-purpose SQL and data-layer modules behind the unified `SqlDataKit` facade.

## Included Modules
- `skill-sql-builder-lite` — Fluent parameterized `SELECT`/`INSERT`/`UPDATE`/`DELETE` SQL builder
- `skill-sql-sanitizer-identifier` — Dialect-aware SQL identifier validator and quoter (`ANSI`, `MySQL`, `PostgreSQL`, `Oracle`)
- `skill-sql-injection-detector` — Pattern-based SQL Injection detector and assertion guard
- `skill-sql-pagination-keyset-seek-builder` — Multi-column zero-offset keyset / seek pagination SQL segment builder
- `skill-sql-sharding-key-modulo-router` — Modulo, consistent hashing, and range-based DB/table sharding router
- `skill-sql-json-path-predicate-builder` — Injection-safe PostgreSQL / MySQL / SQLite JSON path predicate builder

## Gradle Dependency
```groovy
dependencies {
    implementation 'com.msp:starter-sql-data:2.0.0'
}
```

### JitPack
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.rlaguswls13.multi-skill-plugin:starter-sql-data:2.0.0'
}
```

## Quick Start (`SqlDataKit`)
```java
import com.msp.sqljson.SqlJsonDialect;
import com.msp.sqlsanitizer.SqlDialect;
import com.msp.sqlseek.NavigationDirection;
import com.msp.sqlseek.SortSpec;
import com.msp.starter.sqldata.SqlDataKit;

import java.util.List;

// 1. Parameterized SQL Builder
var boundSql = SqlDataKit.select("id", "email")
        .from("users")
        .where("status = ?", "ACTIVE")
        .build();

// 2. Identifier Sanitizer
String quotedTable = SqlDataKit.sanitizeQualifiedIdentifier("public.orders", SqlDialect.POSTGRESQL);

// 3. SQL Injection Guard
SqlDataKit.assertSqlSafe("search-term");

// 4. Keyset / Seek Pagination
var seek = SqlDataKit.buildKeysetSeek(
        List.of(SortSpec.desc("created_at"), SortSpec.desc("id")),
        List.of("2026-05-01T12:00:00Z", 9001L),
        25,
        NavigationDirection.NEXT_PAGE
);

// 5. Sharding Router
var route = SqlDataKit.routeModulo("orders", 4, 4, 17L); // ds_00.orders_001

// 6. Multi-Dialect JSON Path Predicate
var jsonPred = SqlDataKit.jsonEquals(SqlJsonDialect.POSTGRESQL, "payload", "$.user.city", "Seoul");
```
