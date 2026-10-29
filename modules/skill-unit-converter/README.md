# 📐 skill-unit-converter

> **Data Storage Size and High-Resolution Time Duration Unit Converter for Java 21**  
> Pure Java 21 standard library, parsing decimal (SI: 1000) and binary (IEC: 1024) prefixes, composite time strings ("2h 30m"), and human-readable formatters.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 only.
- **`DataSize`**:
  - Full support for Bytes, KB, MB, GB, TB, PB (decimal $10^3$) and KiB, MiB, GiB, TiB, PiB (binary $2^{10}$).
  - Robust text parsing: `DataSize.parse("10.5 MB")`, `DataSize.parse("512 KiB")`, `DataSize.parseBinary(...)`.
  - Human-readable formatting: `toHumanReadable()` (binary default: "1.50 MiB", decimal: "1.50 MB").
- **`TimeDuration`**:
  - Nanosecond resolution up to 292 years (`long` nanos).
  - Composite string parsing: `"2h 30m"`, `"1d 4h 30s"`, `"100ms"`, `"1.5h"`.
  - Seamless interoperability with standard `java.time.Duration`.
  - Human-readable compact formatting: `"2h 30m"`, `"450ms"`, `"1d 4h"`.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-unit-converter:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Data Size Conversions & Parsing

```java
import com.msp.unit.DataSize;
import com.msp.unit.DataUnit;

// Parse human data sizes
DataSize uploadLimit = DataSize.parse("10.5 MB");
System.out.println("Bytes: " + uploadLimit.toBytes()); // 10,500,000

// Convert to other units
double kib = uploadLimit.toKibibytes();
System.out.printf("KiB: %.2f%n", kib);

// Format back to human-readable string
System.out.println(uploadLimit.toHumanReadable()); // "10.01 MiB" (IEC binary)
System.out.println(uploadLimit.toHumanReadable(false)); // "10.50 MB" (SI decimal)
```

### 2. Time Duration Conversions & Parsing

```java
import com.msp.unit.TimeDuration;
import java.time.Duration;

// Parse composite duration string
TimeDuration timeout = TimeDuration.parse("2h 30m");
System.out.println("Total seconds: " + timeout.toSeconds()); // 9000.0

// Arithmetic operations
TimeDuration extended = timeout.plus(TimeDuration.ofMinutes(15));
System.out.println("Extended: " + extended.toHumanReadable()); // "2h 45m"

// Interop with java.time.Duration
Duration standardDuration = timeout.toJavaDuration();
```
