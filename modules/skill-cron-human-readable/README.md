# ⏱️ skill-cron-human-readable

> **Human-readable natural language cron expression translator supporting Korean and English**  
> Pure Java 21 zero-dependency library that parses 5-field, 6-field, and 7-field cron expressions and translates them into fluid, natural sentences in Korean and English.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **5, 6, and 7-Field Cron Parsing**:
  - 5-field standard Unix/Linux cron (`min hour dom month dow`).
  - 6-field Spring/Quartz cron (`sec min hour dom month dow`).
  - 7-field extended Quartz cron (`sec min hour dom month dow year`).
- **Comprehensive Syntax Support**:
  - Wildcards (`*`), step values (`*/step` or `x/step`), ranges (`9-18`), lists (`1,15,30`).
  - Special markers: `?` (no specific value), `L` (last day of month / last weekday), `W` (nearest weekday), `LW` (last weekday).
  - Named months (`JAN`-`DEC`) and named days of week (`SUN`-`SAT`, `0`-`7`).
- **Bilingual Natural Language Translation**:
  - Default Korean (`Locale.KOREAN`): "매 시 정각", "15분마다", "월요일부터 금요일까지 09:00부터 18:00까지 매 시 정각", "매월 마지막 평일 00:00" 등.
  - English (`Locale.ENGLISH`): "Every hour on the hour", "Every 15 minutes", "On the last weekday of the month at 00:00" etc.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-cron-human-readable:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Basic Korean Translation (Default)

```java
import com.msp.crontext.CronHumanReadable;

// 5-field expressions
String desc1 = CronHumanReadable.describe("*/15 * * * *");
// -> "15분마다"

String desc2 = CronHumanReadable.describe("0 9-18 * * 1-5");
// -> "월요일부터 금요일까지 09:00부터 18:00까지 매 시 정각"

String desc3 = CronHumanReadable.describe("0 0 L * *");
// -> "매월 말일 00:00"

String desc4 = CronHumanReadable.describe("0 0 LW * *");
// -> "매월 마지막 평일 00:00"
```

### 2. English Translation

```java
import com.msp.crontext.CronHumanReadable;
import java.util.Locale;

String en1 = CronHumanReadable.describe("*/15 * * * *", Locale.ENGLISH);
// -> "Every 15 minutes"

String en2 = CronHumanReadable.describe("0 30 9 * * ?", Locale.ENGLISH);
// -> "Every day at 09:30:00"

String en3 = CronHumanReadable.describe("0 0 0 ? * 5L", Locale.ENGLISH);
// -> "On the last Friday of the month at 00:00:00"
```

### 3. Parsing CronExpression Record

```java
import com.msp.crontext.CronExpression;

CronExpression expr = CronExpression.parse("0 30 9 * * ?");
System.out.println("Has seconds: " + expr.hasSeconds()); // true
System.out.println("Hour: " + expr.hour());             // "9"
System.out.println("Minute: " + expr.minute());         // "30"
```
