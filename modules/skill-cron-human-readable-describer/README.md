# 🧩 skill-cron-human-readable-describer

> Fluent Korean and English natural language translator for 5-field and 6-field (with seconds) cron expressions with zero 3rd-party dependencies.

---

## 🌟 Key Features

- **Dual-Language Fluency**: Generates natural sentences in both Korean (`describeKorean`) and English (`describeEnglish`).
- **5-Field & 6-Field Support**: Full support for standard Unix (5 fields) and Quartz/Spring (6 fields with seconds).
- **Comprehensive Cron Semantics**: Supports sub-minute/minute/hour intervals (`*/5`), weekday ranges (`1-5`, `MON-FRI`), day-of-month, and annual dates.
- **Strict Validation**: Bounds checks minutes (0-59), hours (0-23), days, and months, throwing clear `IllegalArgumentException` on invalid tokens.
- **Zero Runtime Dependencies**: Pure Java 21 standard library.

---

## 🚀 Quick Start

```java
import com.msp.crondesc.CronDescriber;

// 1. Weekday range
String expr1 = "0 9 * * 1-5";
System.out.println(CronDescriber.describeKorean(expr1));
// Output: 매주 월요일부터 금요일까지 오전 9시 00분마다
System.out.println(CronDescriber.describeEnglish(expr1));
// Output: Every Monday through Friday at 09:00

// 2. Interval
String expr2 = "*/15 * * * *";
System.out.println(CronDescriber.describeKorean(expr2));
// Output: 매 15분마다
System.out.println(CronDescriber.describeEnglish(expr2));
// Output: Every 15 minutes

// 3. Annual execution
String expr3 = "0 0 1 1 *";
System.out.println(CronDescriber.describeKorean(expr3));
// Output: 매년 1월 1일 오전 12시 00분마다
System.out.println(CronDescriber.describeEnglish(expr3));
// Output: Every January 1 at 00:00
```

---

## 🧪 Testing

```bash
.\gradlew.bat :modules:skill-cron-human-readable-describer:test
```
