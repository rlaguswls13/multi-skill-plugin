# 🎯 skill-d-day-calculator

> **D-Day, Korean legal/year/counting age, and anniversary milestone calculator**  
> Pure Java 21 zero-dependency library providing standard D-Day labels (`D-Day`, `D-1`, `D+7`), Korean unified legal age (`만 나이`), calendar year age (`연 나이`), counting age (`세는 나이`), and couple/baby milestones (100일, 1주년).

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 `java.time.*` classes.
- **D-Day Counter**:
  - `D-Day` (event is today).
  - `D-N` (e.g. `D-1`, `D-7` for upcoming events).
  - `D+N` (e.g. `D+1`, `D+7` for elapsed events).
  - Formatted status queries: `isToday()`, `isPast()`, `isFuture()`, `remainingDays()`, `elapsedDays()`.
- **Korean Age Calculations**:
  - **만 나이 (Legal / International Age)**: Based on Korean Unified Age Law (만 나이 통일법). Increments by 1 on each birthday.
  - **연 나이 (Calendar Year Age)**: Used in Military Service Act & Youth Protection Act (`baseYear - birthYear`).
  - **세는 나이 (Traditional Korean Age)**: Traditional counting age (`baseYear - birthYear + 1`).
- **Anniversary Milestones**:
  - `100일`, `200일`, `1000일`: Supports traditional Korean 1-based start date inclusion.
  - `N주년`: Annual anniversary calculator with leap day (Feb 29) handling.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-d-day-calculator:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. D-Day Calculation

```java
import com.msp.dday.DDayCalculator;
import com.msp.dday.DDayResult;
import java.time.LocalDate;

LocalDate examDay = LocalDate.of(2026, 11, 15);
DDayResult result = DDayCalculator.calculate(examDay);

System.out.println(result.label());           // e.g. "D-16"
System.out.println(result.toKoreanSummary()); // e.g. "16일 남음 (D-16)"
```

### 2. Korean Age Calculations

```java
import com.msp.dday.DDayCalculator;
import java.time.LocalDate;

LocalDate birth = LocalDate.of(2000, 5, 15);
LocalDate today = LocalDate.of(2026, 5, 14);

int manAge = DDayCalculator.calculateManAge(birth, today);   // 25 (day before birthday)
int yearAge = DDayCalculator.calculateYearAge(birth, today); // 26 (2026 - 2000)
int countAge = DDayCalculator.calculateCountingAge(birth, today); // 27
```

### 3. Couple / Baby Anniversary (100일, 1주년)

```java
import com.msp.dday.DDayCalculator;
import com.msp.dday.Anniversary;
import java.time.LocalDate;
import java.util.List;

LocalDate startDate = LocalDate.of(2026, 1, 1);

// Korean 100-day anniversary (Day 1 included)
LocalDate day100 = DDayCalculator.anniversary(startDate, 100); // 2026-04-10

// 1 Year Anniversary
LocalDate year1 = DDayCalculator.yearAnniversary(startDate, 1); // 2027-01-01

// Common milestones list
List<Anniversary> milestones = DDayCalculator.commonMilestones(startDate);
```
