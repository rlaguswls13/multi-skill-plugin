# ⏱️ skill-time-ago-formatter

> **Human-friendly relative time formatter supporting Korean and English with configurable thresholds and fallback**  
> Pure Java 21 zero-dependency library that translates Instants, ZonedDateTime, and timestamps into intuitive relative expressions ("방금 전", "5분 전", "어제", "just now", "3 hours ago", "in 10 minutes") with configurable absolute date fallback.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Multilingual Support**: Fully localized in Korean (default) and English.
- **Past & Future Handling**:
  - Korean: `방금 전` / `곧`, `10초 전` / `10초 후`, `5분 전` / `5분 후`, `어제` / `내일`, `3일 전`, `2개월 전`, `1년 전`
  - English: `just now` / `in a moment`, `10 seconds ago` / `in 10 seconds`, `1 minute ago` / `in 1 minute`, `yesterday` / `tomorrow`, `3 days ago`, `2 months ago`, `1 year ago`
- **Fallback Threshold**: Easily configure fallback to standard formats (e.g. `yyyy-MM-dd`) when duration exceeds threshold (e.g. 30 days).
- **Pluggable Clock & ZoneId**: Excellent testability via `Clock.fixed()`.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-time-ago-formatter:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Basic Formatting (Korean & English)

```java
import com.msp.timeago.TimeAgoFormatter;
import java.time.Instant;
import java.time.Duration;
import java.util.Locale;

TimeAgoFormatter formatter = TimeAgoFormatter.standard();

Instant now = Instant.now();
Instant fiveMinutesAgo = now.minus(Duration.ofMinutes(5));

System.out.println(formatter.format(fiveMinutesAgo)); // "5분 전"
System.out.println(formatter.format(fiveMinutesAgo, Locale.ENGLISH)); // "5 minutes ago"
```

### 2. Custom Configuration with Builder

```java
TimeAgoFormatter customFormatter = TimeAgoFormatter.builder()
    .maxRelativeDays(14) // Fallback to yyyy-MM-dd if older than 14 days
    .justNowThresholdSeconds(10) // "방금 전" within 10 seconds
    .build();

String relative = customFormatter.format(Instant.now().minusSeconds(8)); // "방금 전"
```
