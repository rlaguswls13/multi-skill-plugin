# ⏰ Cron Schedule Visualizer (`skill-cron-schedule-visualizer`)

Zero-dependency 5-part cron schedule calculator and ASCII timeline/heatmap visualizer for Java 21.

---

## ✨ Features

- **Zero Runtime Dependencies**: Built entirely on standard Java 21 `java.time.*` (`ZonedDateTime`, `Duration`).
- **Standard 5-Field Cron**: Supports `minute` (0-59), `hour` (0-23), `dayOfMonth` (1-31), `month` (1-12 / JAN-DEC), and `dayOfWeek` (0-7 / SUN-SAT).
- **Flexible Syntax**: Wildcards (`*`), steps (`*/15`), lists (`1,2,5`), ranges (`9-17`), and aliases.
- **Next Execution Calculation**: Computes future execution timestamps strictly after a given time without infinite loops.
- **ASCII Timeline Table**: Formats upcoming triggers and interval durations into a clean monospace ASCII table.
- **24-Hour Distribution Heatmap**: Displays an ASCII bar chart depicting trigger density across the 24 hours of the day over a given number of days.

---

## 🚀 Quick Start

### 1. Calculate Next Executions

```java
import com.msp.cronview.CronScheduleVisualizer;
import java.time.ZonedDateTime;
import java.util.List;

ZonedDateTime now = ZonedDateTime.now();
List<ZonedDateTime> triggers = CronScheduleVisualizer.getNextExecutions("0 9 * * 1-5", now, 5);

triggers.forEach(System.out::println);
```

### 2. Render ASCII Timeline Table

```java
String table = CronScheduleVisualizer.renderTimelineTable("*/15 9-17 * * *", now, 5);
System.out.println(table);
```

Output:
```
Cron Schedule Timeline for: */15 9-17 * * *
+----+----------------------------------+-----+------------------------+
| #  | Trigger Time                     | Day | Interval from Previous |
+----+----------------------------------+-----+------------------------+
| 1  | 2026-11-01 09:00:00 (UTC)        | SUN | -                      |
| 2  | 2026-11-01 09:15:00 (UTC)        | SUN | 15m                    |
| 3  | 2026-11-01 09:30:00 (UTC)        | SUN | 15m                    |
| 4  | 2026-11-01 09:45:00 (UTC)        | SUN | 15m                    |
| 5  | 2026-11-01 10:00:00 (UTC)        | SUN | 15m                    |
+----+----------------------------------+-----+------------------------+
```

### 3. Render 24-Hour Execution Heatmap

```java
String heatmap = CronScheduleVisualizer.renderDailyHeatmap("0,30 9-17 * * *", now, 7);
System.out.println(heatmap);
```

Output:
```
24-Hour Execution Heatmap (7 Days Window) for: 0,30 9-17 * * * (Total Triggers: 126)
Hour | Triggers | Distribution
-----+----------+--------------------------------------------------
 00  | 0        | 
 ...
 09  | 14       | ■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■
 10  | 14       | ■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■
 ...
```

---

## 🛠️ Testing

```bash
./gradlew :modules:skill-cron-schedule-visualizer:test
```
