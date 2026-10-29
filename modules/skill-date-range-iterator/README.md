# 📅 skill-date-range-iterator

> **Fluent, step-aware, policy-based LocalDate and Instant range iterator and stream provider**  
> Pure Java 21 zero-dependency library for creating, stepping, iterating, and querying date intervals with boundary inclusion policies, intersection calculations, and custom periodicity.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 library (`java.time.*`).
- **Flexible Inclusion Policies**:
  - `CLOSED` `[start, end]`: Includes both start and end dates.
  - `HALF_OPEN` `[start, end)`: Standard computer science interval; excludes end date.
  - `OPEN` `(start, end)`: Excludes both boundary dates.
  - `OPEN_CLOSED` `(start, end]`: Excludes start date, includes end date.
- **Customizable Step Amounts**:
  - Step by days, weeks, months, or years via `DateStep` or `Period`.
- **Iterable and Stream-Ready**:
  - Implements `Iterable<LocalDate>` (usable in enhanced `for` loops).
  - Supplies `stream()` for declarative pipeline filtering and mapping.
- **Instant & TimeZone Overloads**:
  - Factory methods to construct intervals from `Instant` and `ZoneId`.
- **Interval Algebra**:
  - `overlaps(DateRange other)`: Detects date overlaps.
  - `intersection(DateRange other)`: Computes sub-range overlap intersection.
  - `contains(LocalDate)`: Tests membership according to policy.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-date-range-iterator:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Basic Iteration with Enhanced For-Loop

```java
import com.msp.daterange.DateRange;
import java.time.LocalDate;

DateRange range = DateRange.closed(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 5)
);

for (LocalDate date : range) {
    System.out.println(date); // 2026-01-01 through 2026-01-05
}
```

### 2. Stream Processing with Step Units

```java
import com.msp.daterange.DateRange;
import com.msp.daterange.DateStep;
import java.time.LocalDate;
import java.util.List;

// Iterate every Monday for a quarter
DateRange quarter = DateRange.closed(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 3, 31)
).withStep(DateStep.ofWeeks(1));

List<LocalDate> weeklyDates = quarter.stream()
        .filter(d -> d.getDayOfMonth() <= 7) // first week of each month
        .toList();
```

### 3. Date Overlap and Intersection (Booking/Shift Guards)

```java
DateRange shift1 = DateRange.closed(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10));
DateRange shift2 = DateRange.closed(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 15));

if (shift1.overlaps(shift2)) {
    DateRange conflict = shift1.intersection(shift2).orElseThrow();
    System.out.println("Conflict dates: " + conflict); // [2026-05-08, 2026-05-10]
}
```
