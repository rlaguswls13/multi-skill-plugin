# skill-sql-pagination-keyset-seek-builder

Multi-Column SQL Keyset / Seek Pagination Predicate & `ORDER BY` Builder for zero-offset deep pagination in pure Java 21.

## Features
- **Multi-Column Sort Specifications**: Supports arbitrary numbers of sort columns with mixed `ASC` / `DESC` directions (e.g., `created_at DESC, priority ASC, id DESC`).
- **Expanded Disjunctive Predicate Style**: Universal across all SQL RDBMS and supports mixed `ASC`/`DESC` sort directions:
  ```sql
  (created_at < ? OR (created_at = ? AND priority > ?) OR (created_at = ? AND priority = ? AND id < ?))
  ```
- **Row-Value Constructor Tuple Style**: Compact tuple comparison for uniform `ASC` or `DESC` sort directions in PostgreSQL, MySQL, SQLite, and H2:
  ```sql
  (created_at, id) < (?, ?)
  ```
- **Bi-Directional Seek Navigation**: Supports `NEXT_PAGE` and `PREVIOUS_PAGE` (automatically inverting inequalities and `ORDER BY` directions).
- **SQL Injection Safe**: Validates column identifiers and produces positional `?` bind parameters in `SeekQuerySegment`.

## Usage Example

```java
package com.msp.sqlseek.example;

import com.msp.sqlseek.*;

public class KeysetPaginationExample {
    public static void main(String[] args) {
        // 1. Mixed ASC/DESC Expanded Disjunctive Query
        SeekQuerySegment segment = KeysetSeekBuilder.builder()
                .sortDesc("created_at")
                .sortAsc("priority")
                .sortDesc("id")
                .predicateStyle(PredicateStyle.EXPANDED_DISJUNCTIVE)
                .navigationDirection(NavigationDirection.NEXT_PAGE)
                .cursorValues("2026-11-19T00:00:00Z", 3, 1050L)
                .pageSize(25)
                .build();

        System.out.println("WHERE: " + segment.wherePredicate());
        System.out.println("ORDER BY: " + segment.orderByClause());
        System.out.println("LIMIT: " + segment.limitClause());
        System.out.println("Bind Params: " + segment.bindParameters());

        // 2. Uniform DESC Row-Value Tuple Query
        SeekQuerySegment tupleSegment = KeysetSeekBuilder.builder()
                .sortDesc("created_at")
                .sortDesc("id")
                .predicateStyle(PredicateStyle.ROW_VALUE_TUPLE)
                .cursorValues("2026-11-19T00:00:00Z", 1050L)
                .pageSize(25)
                .build();

        System.out.println("Tuple Suffix: " + tupleSegment.toSqlSuffix());
    }
}
```
