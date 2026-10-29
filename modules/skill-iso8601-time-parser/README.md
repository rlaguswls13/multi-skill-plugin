# 🕒 skill-iso8601-time-parser

> **Multi-format resilient ISO-8601, RFC-1123, epoch, and standard date-time string parser**  
> Pure Java 21 zero-dependency library that seamlessly parses diverse date and time formats into `Instant`, `ZonedDateTime`, and `LocalDate` without crashing or throwing exceptions.

---

## 🚀 Supported Formats

- **ISO-8601**:
  - `2026-10-30T12:00:00Z`
  - `2026-10-30T21:00:00+09:00`
  - `2026-10-30T12:00:00.123456Z`
- **RFC-1123 (HTTP Standard)**:
  - `Fri, 30 Oct 2026 12:00:00 GMT`
- **Standard Date-Time**:
  - `yyyy-MM-dd HH:mm:ss[.SSS]` (e.g. `2026-10-30 12:00:00`)
  - `yyyy/MM/dd HH:mm:ss[.SSS]`
  - `yyyy.MM.dd HH:mm:ss[.SSS]`
  - `yyyyMMddHHmmss` (compact)
- **Date-Only**:
  - `yyyy-MM-dd`, `yyyy/MM/dd`, `yyyy.MM.dd`, `yyyyMMdd`
- **Epoch Timestamps**:
  - Seconds (10 digits: `1730289600`)
  - Milliseconds (13 digits: `1730289600000`)
  - Microseconds (16 digits) and Nanoseconds (19 digits)

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-iso8601-time-parser:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Parsing to Instant

```java
import com.msp.timeparse.Iso8601TimeParser;
import java.time.Instant;
import java.util.Optional;

// ISO-8601
Optional<Instant> t1 = Iso8601TimeParser.parseToInstant("2026-10-30T12:00:00Z");

// Epoch timestamp string
Optional<Instant> t2 = Iso8601TimeParser.parseToInstant("1730289600");

// Standard human-readable format
Optional<Instant> t3 = Iso8601TimeParser.parseToInstant("2026-10-30 12:00:00");
```

### 2. Parsing to LocalDate

```java
Optional<LocalDate> date1 = Iso8601TimeParser.parseToLocalDate("20261030"); // 2026-10-30
Optional<LocalDate> date2 = Iso8601TimeParser.parseToLocalDate("2026/10/30"); // 2026-10-30
```
