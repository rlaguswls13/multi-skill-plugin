# skill-sql-upsert-dialect-formatter

> Pure Java multi-dialect SQL UPSERT (Insert or Update) statement generator supporting PostgreSQL, SQLite, MySQL, MariaDB, Oracle, H2, and SQL Server with zero runtime dependencies.

---

## 🚀 Features

- **Multi-Dialect Support**:
  - **PostgreSQL / SQLite**: `INSERT INTO ... VALUES (...) ON CONFLICT (id) DO UPDATE SET ...` or `DO NOTHING`.
  - **MySQL / MariaDB**: `INSERT INTO ... VALUES (...) ON DUPLICATE KEY UPDATE col = VALUES(col)` and `INSERT IGNORE INTO ...`.
  - **Oracle / H2**: Standard `MERGE INTO target USING (SELECT ... FROM DUAL) ... WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT ...`.
  - **SQL Server**: `MERGE INTO target AS target USING (VALUES (...)) AS source (...) ...;` terminating with semicolon.
- **Parameter Binding Modes**:
  - `ParameterStyle.POSITIONAL` (`?` for JDBC PreparedStatement)
  - `ParameterStyle.NAMED_COLON` (`:name`)
  - `ParameterStyle.NAMED_DOLLAR` (`${name}`)
- **Custom Update Expressions**:
  - Support for SQL functions and formulas like `views = views + 1`, `updated_at = NOW()`.
- **Composite Conflict Keys**:
  - Multi-column unique constraints (e.g. `(tenant_id, user_id)`).
- **Pure Java 21**: Zero 3rd-party dependencies.

---

## 📦 Usage Examples

### 1. Basic PostgreSQL Upsert

```java
import com.msp.sqlupsert.*;

UpsertStatement stmt = UpsertBuilder.into("users")
    .conflictKeys("email")
    .value("email", "alice@example.com")
    .value("name", "Alice")
    .value("score", 100)
    .updateOnConflict("name", "score")
    .forDialect(DatabaseDialect.POSTGRESQL)
    .build();

System.out.println(stmt.sql());
// INSERT INTO users (email, name, score)
// VALUES (?, ?, ?)
// ON CONFLICT (email) DO UPDATE SET
//   name = EXCLUDED.name,
//   score = EXCLUDED.score

System.out.println(stmt.parameters());
// [alice@example.com, Alice, 100]
```

### 2. MySQL / MariaDB Upsert

```java
UpsertStatement stmt = UpsertBuilder.into("users")
    .value("id", 1)
    .value("name", "Bob")
    .value("status", "ACTIVE")
    .updateOnConflict("name", "status")
    .forDialect(DatabaseDialect.MYSQL)
    .build();

System.out.println(stmt.sql());
// INSERT INTO users (id, name, status)
// VALUES (?, ?, ?)
// ON DUPLICATE KEY UPDATE
//   name = VALUES(name),
//   status = VALUES(status)
```

### 3. Oracle & SQL Server MERGE

```java
UpsertStatement stmt = UpsertBuilder.into("accounts")
    .conflictKeys("account_id")
    .value("account_id", "acc_100")
    .value("balance", 5000)
    .updateOnConflict("balance")
    .forDialect(DatabaseDialect.ORACLE)
    .build();

System.out.println(stmt.sql());
// MERGE INTO accounts t
// USING (SELECT ? AS account_id, ? AS balance FROM DUAL) s
// ON (t.account_id = s.account_id)
// WHEN MATCHED THEN
//   UPDATE SET
//     t.balance = s.balance
// WHEN NOT MATCHED THEN
//   INSERT (account_id, balance)
//   VALUES (s.account_id, s.balance)
```

---

## 🧪 Testing

```bash
./gradlew :modules:skill-sql-upsert-dialect-formatter:test
```
