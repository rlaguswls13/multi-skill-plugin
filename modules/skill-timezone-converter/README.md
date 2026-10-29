# 🌐 skill-timezone-converter

> **Timezone conversion and offset difference calculator with standard alias resolution**  
> Pure Java 21 zero-dependency library for cross-timezone conversions, DST detection, alias lookup (KST, PST, EST, UTC), and duration-based offset differences.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Convenient Alias Resolution**: Map aliases (`KST`, `JST`, `PST`, `EST`, `CET`, `BST`, `UTC`, `GMT`, etc.) directly to standard IANA `ZoneId`.
- **Accurate Conversions**: `convert(ZonedDateTime, ZoneId)` and `convert(Instant, ZoneId)` preserving precise moments in time.
- **Offset Difference Math**: `getOffsetDifference(zone1, zone2, instant)` dynamically computing relative offset accounting for Daylight Saving Time (DST).
- **Daylight Saving Time (DST) Detection**: `isDaylightSavingTime(dateTime)`.
- **Formatting Utilities**: ISO-8601 offset strings and custom pattern formatting.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-timezone-converter:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Converting Between Timezones

```java
import com.msp.tzconvert.TimezoneConverter;
import java.time.ZonedDateTime;
import java.time.ZoneId;

ZonedDateTime utcTime = ZonedDateTime.now(ZoneId.of("UTC"));

// Convert UTC to KST using alias
ZonedDateTime kstTime = TimezoneConverter.convert(utcTime, "KST");

// Convert to US Pacific Time
ZonedDateTime pstTime = TimezoneConverter.convert(utcTime, "PST");
```

### 2. Timezone Offset Difference & DST

```java
import java.time.Instant;
import java.time.Duration;

// UTC to KST is +9 hours
Duration diff = TimezoneConverter.getOffsetDifference("UTC", "KST", Instant.now());
System.out.println("Offset difference hours: " + diff.toHours()); // 9

// Check if New York is currently on Daylight Saving Time
boolean isDst = TimezoneConverter.isDaylightSavingTime(ZonedDateTime.now(ZoneId.of("America/New_York")));
```
