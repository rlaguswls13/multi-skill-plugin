# 📊 skill-csv-to-sqlite-converter

> **Zero-dependency streaming CSV to SQLite DDL and batch INSERT statements converter**  
> Pure Java 21 library for parsing CSV files, inferring SQLite data types (`INTEGER`, `REAL`, `TEXT`), and generating batch SQL dump scripts.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 I/O only.
- **Smart Type Inference**: Automatically analyzes initial rows to infer `INTEGER`, `REAL`, or `TEXT` data types.
- **RFC-4180 Compliant**: Handles commas, newlines within quotes, and escaped quotes (`""`).
- **Memory-Efficient Streaming**: Emits DDL and chunked `INSERT INTO` statements to a streaming consumer without holding entire files in memory.
- **Safe SQL Sanitization**: Escapes table and column identifiers with square brackets `[...]` and single quotes `''`.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-csv-to-sqlite-converter:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Generating SQL Script from CSV

```java
import com.msp.csvsqlite.CsvSqliteConverter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

CsvSqliteConverter converter = new CsvSqliteConverter();

try (InputStream in = Files.newInputStream(Path.of("data/users.csv"))) {
    String sql = converter.generateSqlScript(in, "users");
    Files.writeString(Path.of("dump.sql"), sql);
}
```

### 2. Streaming Execution into SQLite Database / Pipe

```java
try (InputStream in = Files.newInputStream(Path.of("large_dataset.csv"))) {
    converter.convert(in, "analytics_events", 200 /* batchSize */, statement -> {
        // Execute each SQL statement against JDBC SQLite connection or stream to file
        jdbcStatement.execute(statement);
    });
}
```
