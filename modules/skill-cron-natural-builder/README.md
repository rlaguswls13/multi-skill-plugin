# Cron Natural Builder (`skill-cron-natural-builder`)

Fluent, type-safe builder for standard 5-field (Unix/POSIX) and 6-field (Quartz/Spring) Cron expressions in pure Java 21.

## Features
- **Zero Runtime Dependencies**: Standard Java 21 only.
- **5-Field & 6-Field Support**: Clean transition between standard 5-part cron (`* * * * *`) and 6-part cron (`0 * * * * *`) via `.withSeconds()`.
- **Fluent & Readable API**: High readability for everyday scheduling scenarios (`everyMinute()`, `dailyAt(14, 30)`, `weeklyOn(DayOfWeek.MONDAY, 9, 0)`, `monthlyOn(...)`).
- **Strict Boundary Validation**: Validates minute (0-59), hour (0-23), dayOfMonth (1-31), month (1-12), and second (0-59) to prevent invalid cron strings.

## Usage Examples

### 1. Common Natural Schedules
```java
import com.msp.cronbuilder.CronBuilder;
import java.time.DayOfWeek;

// Every 5 minutes
String every5m = CronBuilder.everyMinutes(5).build(); // "*/5 * * * *"

// Daily at 02:30 PM (14:30)
String daily230 = CronBuilder.dailyAt(14, 30).build(); // "30 14 * * *"

// Weekly every Monday at 09:00 AM
String weekly = CronBuilder.weeklyOn(DayOfWeek.MONDAY, 9, 0).build(); // "0 9 * * 1"

// Monthly on the 1st at midnight
String monthly = CronBuilder.monthlyOn(1, 0, 0).build(); // "0 0 1 * *"
```

### 2. 6-Field Cron (Quartz / Spring Format)
```java
// Quartz/Spring style with seconds field
String cron6 = CronBuilder.dailyAt(9, 0).withSeconds().build(); // "0 0 9 * * *"
String customSeconds = CronBuilder.everyMinute().withSeconds(30).build(); // "30 * * * * *"
```

### 3. Custom Combinations & Workdays
```java
// Weekdays (Monday to Friday) at 9:00 AM
String workStart = CronBuilder.create().at(9, 0).weekdays().build(); // "0 9 * * 1-5"

// Weekends at noon
String weekend = CronBuilder.create().at(12, 0).weekends().build(); // "0 12 * * 6,7"
```
