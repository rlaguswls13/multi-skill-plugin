# SQL Identifier Sanitizer (`skill-sql-sanitizer-identifier`)

Production-grade, zero-dependency SQL identifier validator and sanitizer preventing SQL Injection attacks when dynamically interpolating table names, column names, schema names, and aliases into SQL queries.

## Features
- **Dialect Support**: `ANSI_STANDARD` (`"name"`), `MYSQL` (`` `name` ``), `POSTGRESQL` (`"name"`), `ORACLE` (`"name"`).
- **Strict Injection Defense**: Rejects null characters (`\0`), semicolons (`;`), SQL comments (`--`, `/*`), single quotes (`'`), control characters, and injection operators (`=`, `<`, `>`).
- **Internal Quote Escaping**: Properly doubles internal dialect quotation marks (e.g., `col"name` -> `"col""name"`, `col`name` -> `` `col``name` ``).
- **Qualified Identifier Sanitization**: Handles multi-part paths like `schema.table.column` safely.
- **Reserved Keywords**: Safely quotes reserved words (`SELECT`, `ORDER`, `GROUP`, etc.) so the database treats them as valid identifiers.

## Usage

```java
import com.msp.sqlsanitizer.SqlDialect;
import com.msp.sqlsanitizer.SqlIdentifierSanitizer;

public class Example {
    public static void main(String[] args) {
        // 1. Validation check
        boolean valid = SqlIdentifierSanitizer.isValidIdentifier("user_account"); // true
        boolean safe = SqlIdentifierSanitizer.isValidIdentifier("users; DROP TABLE users;--"); // false

        // 2. Simple sanitization
        String table = SqlIdentifierSanitizer.sanitize("users", SqlDialect.ANSI_STANDARD);
        // Result: "users"

        String colMySql = SqlIdentifierSanitizer.sanitize("order_count", SqlDialect.MYSQL);
        // Result: `order_count`

        // 3. Qualified path sanitization
        String qualified = SqlIdentifierSanitizer.sanitizeQualified("app_db.users", SqlDialect.ANSI_STANDARD);
        // Result: "app_db"."users"
    }
}
```
