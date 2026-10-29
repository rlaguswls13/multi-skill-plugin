# 📅 skill-business-day-calculator

> **Business day calculation library with configurable weekends, custom holidays, and date stepping**  
> Pure Java 21 zero-dependency library for computing working business days, skipping weekends and holidays, stepping forward/backward, and counting business day durations.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Configurable Weekends**: Default Saturday/Sunday, or customizable (e.g. Middle Eastern Friday/Saturday, 6-day work weeks).
- **Flexible Holidays**: Pluggable via `HolidayPredicate` or `Set<LocalDate>`. Can be easily integrated with `KoreanHolidayCalculator`.
- **Date Stepping**:
  - `addBusinessDays(date, n)`: moves forward ($n > 0$) or backward ($n < 0$) across weekends and holidays.
  - `nextBusinessDay(date)` / `previousBusinessDay(date)`.
- **Duration Counting**:
  - `countBusinessDays(start, end)`: half-open interval $[start, end)$.
  - `countBusinessDaysInclusive(start, end)`: closed interval $[start, end]$.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-business-day-calculator:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Standard Business Day Checks & Math

```java
import com.msp.bizday.BusinessDayCalculator;
import java.time.LocalDate;

BusinessDayCalculator calc = BusinessDayCalculator.standard();

LocalDate friday = LocalDate.of(2024, 5, 3);
LocalDate monday = calc.nextBusinessDay(friday); // 2024-05-06

// Add 3 business days from Wednesday
LocalDate wed = LocalDate.of(2024, 5, 1);
LocalDate target = calc.addBusinessDays(wed, 3); // 2024-05-06 (Mon)
```

### 2. Integration with Korean Holiday Calculator

```java
import com.msp.bizday.BusinessDayCalculator;
import com.msp.holiday.KoreanHolidayCalculator;

KoreanHolidayCalculator holidayCalc = KoreanHolidayCalculator.getInstance();
BusinessDayCalculator krBizCalc = BusinessDayCalculator.of(holidayCalc::isHoliday);

// 2024-05-03 is Friday, 2024-05-06 is Children's Day substitute holiday (Monday)
LocalDate nextWorkDay = krBizCalc.nextBusinessDay(LocalDate.of(2024, 5, 3));
// Output: 2024-05-07 (Tuesday)
```

### 3. Counting Business Days Between Dates

```java
LocalDate start = LocalDate.of(2024, 5, 6);
LocalDate end = LocalDate.of(2024, 5, 13);

long workingDays = calc.countBusinessDays(start, end); // 5 days
```
