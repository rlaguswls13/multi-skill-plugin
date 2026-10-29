# 🔄 skill-type-converter

Safe string to primitive, wrapper, enum, date, list converter with fallback defaults and zero runtime dependencies.

## ✨ Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Null-Safe & Exception-Resilient**: Never throws unexpected `NumberFormatException` or `DateTimeParseException` when using default fallback methods.
- **Rich Type Support**:
  - `int` / `Integer`, `long` / `Long`, `double` / `Double`, `float` / `Float`
  - `boolean` / `Boolean`: supports `true`/`false`, `1`/`0`, `yes`/`no`, `y`/`n`, `on`/`off`
  - `Enum`: case-insensitive and hyphen/underscore tolerant
  - `LocalDate`: multi-format (`yyyy-MM-dd`, `yyyyMMdd`, `yyyy/MM/dd`, `yyyy.MM.dd`, `dd-MM-yyyy`, etc.)
  - `LocalDateTime`: ISO and standard space-separated patterns (`yyyy-MM-dd HH:mm:ss`, `yyyyMMddHHmmss`)
  - `List<T>`: delimiter-separated string parsing with trimming and mapper support
- **Flexible Modes**:
  - Lenient with fallback: `TypeConverters.toInteger("bad", 0)`
  - Strict: `TypeConverters.toIntegerOrThrow("bad")`
  - Result Pattern: `TypeConverters.tryConvert("42", Integer::parseInt)` returning `ConversionResult<T>`

## 🚀 Usage

```java
import com.msp.convert.TypeConverters;
import java.time.LocalDate;
import java.util.List;

// Safe conversions with fallbacks
int port = TypeConverters.toInt(System.getenv("PORT"), 8080);
boolean debug = TypeConverters.toBoolean("yes", false); // true
LocalDate date = TypeConverters.toLocalDate("20261030", LocalDate.now()); // 2026-10-30

// Delimited lists
List<String> tags = TypeConverters.toList("java, spring, gradle", ",");
List<Integer> ids = TypeConverters.toList("10, 20, 30", ",", Integer::parseInt);

// Strict conversion
int age = TypeConverters.toIntegerOrThrow("25");
```
