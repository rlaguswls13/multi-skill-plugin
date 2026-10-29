# 📊 skill-fiscal-calendar

> **Configurable fiscal year, quarter, half, and week calendar utility**  
> Pure Java 21 zero-dependency library for calculating corporate and governmental fiscal periods, quarterly milestones (Q1-Q4), halves (H1-H2), and fiscal week numbers.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 `java.time.*` classes.
- **Configurable Fiscal Start Month**:
  - `standard()`: January start (Korea, China, Germany).
  - `usFederal()`: October start (US Federal Government).
  - `ukJapan()`: April start (UK, Japan, Canada, India).
  - `australian()`: July start (Australia, New Zealand).
  - Custom month: `FiscalCalendar.of(Month.MARCH)`.
- **Year Naming Conventions**:
  - `END_YEAR`: Named after the calendar year in which the fiscal year concludes (e.g. Oct 2026 – Sep 2027 is `FY2027`).
  - `START_YEAR`: Named after the calendar year in which it begins.
- **Comprehensive Period Decompositions**:
  - `FiscalYear`: Year number, code ("FY2027"), start date, end date, `contains(LocalDate)`.
  - `FiscalQuarter`: Quarter number (1-4), code ("FY2027-Q1"), start/end date.
  - `FiscalHalf`: Half number (1-2), code ("FY2027-H1").
  - `getFiscalWeek(LocalDate)`: 1-53 week index.
  - `getFiscalMonth(LocalDate)`: 1-12 fiscal month index.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-fiscal-calendar:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. US Federal Government Fiscal Calendar (October Start)

```java
import com.msp.fiscal.FiscalCalendar;
import com.msp.fiscal.FiscalQuarter;
import com.msp.fiscal.FiscalYear;
import java.time.LocalDate;

FiscalCalendar calendar = FiscalCalendar.usFederal();

LocalDate date = LocalDate.of(2026, 10, 15);

FiscalYear fy = calendar.getFiscalYear(date);
System.out.println(fy.code());      // "FY2027"
System.out.println(fy.startDate()); // 2026-10-01
System.out.println(fy.endDate());   // 2027-09-30

FiscalQuarter fq = calendar.getFiscalQuarter(date);
System.out.println(fq.code());      // "FY2027-Q1"
System.out.println(fq.startDate()); // 2026-10-01
System.out.println(fq.endDate());   // 2026-12-31

int week = calendar.getFiscalWeek(date); // 3
```

### 2. Direct Period Lookup

```java
FiscalCalendar calendar = FiscalCalendar.ukJapan(); // April start

// Query Q3 of FY2026 directly:
FiscalQuarter q3 = calendar.getQuarter(2026, 3);
System.out.println(q3.startDate()); // 2025-10-01
System.out.println(q3.endDate());   // 2025-12-31
```
