# SQL Window Function Builder (`skill-sql-window-function-builder`)

Type-safe, zero-dependency SQL Window Function Builder for Java 21, generating standard SQL:2003+ window expressions:
`ROW_NUMBER`, `RANK`, `DENSE_RANK`, `PERCENT_RANK`, `CUME_DIST`, `NTILE`, `LEAD`, `LAG`, `FIRST_VALUE`, `LAST_VALUE`, `NTH_VALUE`, `SUM`, `AVG`, `COUNT`, `MIN`, `MAX`.

## Features

- **Standard Window Function Catalog**:
  - Ranking: `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()`, `PERCENT_RANK()`, `CUME_DIST()`, `NTILE(n)`
  - Value / Offsets: `LEAD(col, offset, default)`, `LAG(col, offset, default)`, `FIRST_VALUE(col)`, `LAST_VALUE(col)`, `NTH_VALUE(col, n)`
  - Aggregates: `SUM(col)`, `AVG(col)`, `COUNT(col)`, `COUNT(*)`, `MIN(col)`, `MAX(col)`
  - Custom functions: `WindowFunctions.custom(name, args...)`
- **Window Specification (`OVER`)**:
  - `PARTITION BY`: multiple columns / expressions
  - `ORDER BY`: `ASC`, `DESC`, `NULLS FIRST`, `NULLS LAST`
  - Frame Clause: `ROWS`, `RANGE`, `GROUPS` with bounds (`UNBOUNDED PRECEDING`, `n PRECEDING`, `CURRENT ROW`, `n FOLLOWING`, `UNBOUNDED FOLLOWING`) and exclusion (`EXCLUDE CURRENT ROW`, `EXCLUDE GROUP`, `EXCLUDE TIES`)
  - Named Windows: `OVER w`
- **Immutable & Reusable**: `WindowSpec` can be defined once and reused across multiple expressions.
- **Pure Java 21**: Zero runtime dependencies.

## Usage Example

```java
import com.msp.sqlwindow.WindowFunctions;
import com.msp.sqlwindow.OrderSpec;
import com.msp.sqlwindow.FrameBound;

// 1. Department rank by salary
String sql1 = WindowFunctions.rowNumber()
    .over()
    .partitionBy("department_id")
    .orderBy(OrderSpec.desc("salary"))
    .as("dept_rank")
    .toSql();
// -> ROW_NUMBER() OVER (PARTITION BY department_id ORDER BY salary DESC) AS dept_rank

// 2. Running total with frame clause
String sql2 = WindowFunctions.sum("amount")
    .over()
    .partitionBy("customer_id")
    .orderBy("order_date")
    .rowsBetween(FrameBound.unboundedPreceding(), FrameBound.currentRow())
    .as("running_total")
    .toSql();
// -> SUM(amount) OVER (PARTITION BY customer_id ORDER BY order_date ASC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total

// 3. LEAD with default value
String sql3 = WindowFunctions.lead("salary", 1, 0)
    .over()
    .partitionBy("department_id")
    .orderBy("hire_date")
    .as("next_salary")
    .toSql();
// -> LEAD(salary, 1, 0) OVER (PARTITION BY department_id ORDER BY hire_date ASC) AS next_salary
```
