# skill-sql-ast-simple-visitor

> Pure Java SQL Abstract Syntax Tree (AST) Parser and Visitor Engine for schema analysis, column extraction, and SQL injection security guards.

---

## 🚀 Features

- **Lightweight SQL AST Hierarchy**:
  - `SqlNode`: Base visitor-accepting interface.
  - `SelectQueryNode`: Root SELECT node supporting projections, FROM table, JOINs, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT, and OFFSET.
  - `TableRefNode`: Table name with optional schema and alias.
  - `ColumnRefNode`: Column name with optional table qualifier.
  - `BinaryOpNode`: Binary operations (`=`, `!=`, `<`, `>`, `LIKE`, `AND`, `OR`, etc.).
  - `LiteralNode`: String, Number, Boolean, or NULL values.
  - `JoinNode`, `OrderByNode`, `InListNode`, `FunctionCallNode`, `WildcardNode`.
- **Pre-built AST Visitors**:
  - `TableCollectorVisitor`: Discovers all referenced tables, schema qualifications, and aliases.
  - `ColumnCollectorVisitor`: Categorizes column references across projections, filters, groupings, and orderings.
  - `SqlInjectionGuardVisitor`: Detects literal tautologies (`'1' = '1'`, `1 = 1`), self-comparisons (`id = id`), and authorization bypass OR conditions.
  - `SqlAstSqlPrinter`: Reconstructs normalized SQL strings from AST.
- **Embedded SQL Parser**:
  - `SimpleSqlAstParser.parse(sql)`: Parses common SQL queries into typed AST nodes without any heavy 3rd-party dependencies.
- **Pure Java 21**: Zero runtime dependencies.

---

## 📦 Usage Examples

### 1. Extract Referenced Tables and Columns

```java
import com.msp.sqlvisitor.*;

String sql = "SELECT u.name, o.amount FROM accounts.users u " +
             "LEFT JOIN accounts.orders o ON u.id = o.user_id " +
             "WHERE u.status = 'ACTIVE' ORDER BY o.amount DESC";

SelectQueryNode ast = SimpleSqlAstParser.parse(sql);

// Collect tables
TableCollectorVisitor tableCollector = new TableCollectorVisitor();
ast.accept(tableCollector);
System.out.println("Tables: " + tableCollector.getTableNames());
// => [accounts.users, accounts.orders]

// Collect columns
ColumnCollectorVisitor columnCollector = new ColumnCollectorVisitor();
ast.accept(columnCollector);
System.out.println("Filter Columns: " + columnCollector.getFilterColumns());
```

### 2. Guard Against SQL Injection Tautologies

```java
String maliciousSql = "SELECT * FROM users WHERE username = 'admin' OR '1' = '1'";

SelectQueryNode ast = SimpleSqlAstParser.parse(maliciousSql);
SqlInjectionGuardVisitor guard = new SqlInjectionGuardVisitor();
ast.accept(guard);

if (!guard.isSafe()) {
    for (SecurityAlert alert : guard.getAlerts()) {
        System.err.println("[" + alert.severity() + "] " + alert.ruleId() + ": " + alert.message());
    }
}
```

### 3. Build AST Programmatically & Format

```java
SelectQueryNode query = SelectQueryNode.builder()
        .select("id", "username")
        .from("members", "m")
        .where(new BinaryOpNode(new ColumnRefNode("m", "level"), ">=", LiteralNode.ofNumber(10)))
        .orderBy("m.id", SortDirection.ASC)
        .limit(50)
        .build();

String sql = query.accept(new SqlAstSqlPrinter());
System.out.println(sql);
```
