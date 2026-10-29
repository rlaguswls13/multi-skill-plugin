# 🇰🇷 skill-korean-holiday-calculator

> **Korean national and substitute holiday calculator (solar and lunar accurate for 2018–2040)**  
> Pure Java 21 zero-dependency library implementing South Korea's official public holiday regulations ("관공서의 공휴일에 관한 규정" 및 동 시행령 제2조, 제2조의2), including accurate astronomical lunar holidays and automatic substitute holiday derivation.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Fixed Solar Holidays**: 신정 (1/1), 삼일절 (3/1), 어린이날 (5/5), 현충일 (6/6), 광복절 (8/15), 개천절 (10/3), 한글날 (10/9), 기독탄신일/성탄절 (12/25).
- **Accurate Lunar Holidays (2018–2040)**:
  - 설날 연휴 (전날, 설날 당일, 다음날)
  - 부처님오신날 (석가탄신일, 음력 4/8)
  - 추석 연휴 (전날, 추석 당일, 다음날)
- **Legal Substitute Holiday Derivation**:
  - Automatically calculates substitute holidays when qualifying holidays fall on weekends or collide with other holidays.
  - Covers 삼일절, 광복절, 개천절, 한글날, 어린이날, 부처님오신날, 성탄절, 설날/추석 연휴.
- **Custom / Temporary Holidays**: Support for election days or temporary government-declared holidays via `KoreanHolidayCalculator.builder()`.
- **High Performance & Thread-Safe**: Concurrent caching per year, zero mutable state.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-korean-holiday-calculator:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Check if a Date is a Holiday

```java
import com.msp.holiday.KoreanHolidayCalculator;
import com.msp.holiday.Holiday;
import java.time.LocalDate;

KoreanHolidayCalculator calculator = KoreanHolidayCalculator.getInstance();

// 2024-05-05 (어린이날 - 일요일)
boolean isMay5 = calculator.isHoliday(LocalDate.of(2024, 5, 5)); // true

// 2024-05-06 (어린이날 대체공휴일 - 월요일)
boolean isMay6 = calculator.isHoliday(LocalDate.of(2024, 5, 6)); // true
boolean isSubstitute = calculator.isSubstituteHoliday(LocalDate.of(2024, 5, 6)); // true

calculator.getHoliday(LocalDate.of(2024, 5, 6)).ifPresent(holiday -> {
    System.out.println("Holiday Name: " + holiday.name()); // 대체공휴일 (어린이날)
    System.out.println("Type: " + holiday.type());         // SUBSTITUTE
});
```

### 2. Retrieve All Holidays for a Specific Year

```java
List<Holiday> holidays2024 = calculator.getHolidays(2024);
for (Holiday h : holidays2024) {
    System.out.printf("%s : %s (대체여부: %s)%n", h.date(), h.name(), h.isSubstitute());
}
```

### 3. Adding Custom / Temporary Holidays

```java
KoreanHolidayCalculator customCalc = KoreanHolidayCalculator.builder()
    .addCustomHoliday(LocalDate.of(2024, 4, 10), "제22대 국회의원 선거일")
    .build();

boolean isElection = customCalc.isHoliday(LocalDate.of(2024, 4, 10)); // true
```
