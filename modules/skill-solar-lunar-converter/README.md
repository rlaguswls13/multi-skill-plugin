# 🌙 skill-solar-lunar-converter

> **Pure Java bidirectional Solar to Lunar (Gregorian to Lunisolar) astronomical converter**  
> Pure Java 21 zero-dependency library using a 201-element bit-packed lookup table (< 1KB) for bidirectional conversions between the Gregorian calendar and East Asian lunisolar (음력) calendar from 1900 to 2100.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 standard library (`java.time.*`).
- **Bidirectional 100% Round-Trip Guarantee**:
  - `toLunar(LocalDate solarDate) -> LunarDate`
  - `toSolar(LunarDate lunarDate) -> Optional<LocalDate>`
  - `toSolar(int year, int month, int day, boolean isLeapMonth) -> Optional<LocalDate>`
- **Ultra-Compact Astronomical Lookup Table**:
  - Encoded across 201 integers (804 bytes total).
  - Bit-packed with 12 regular month lengths, leap month presence, and leap month length.
- **Full Leap Month (윤달) Handling**:
  - Correctly calculates whether a month is regular (평달) or intercalary leap (윤달) (e.g. 2023 윤2월).
- **Korean Formatting**:
  - `toKoreanString()`: e.g. "음력 2026년 1월 1일", "음력 2023년 윤2월 15일".

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-solar-lunar-converter:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Solar to Lunar Conversion

```java
import com.msp.solarlunar.SolarLunarConverter;
import com.msp.solarlunar.LunarDate;
import java.time.LocalDate;

// Seollal (설날) 2024
LocalDate seollalSolar = LocalDate.of(2024, 2, 10);
LunarDate lunar = SolarLunarConverter.toLunar(seollalSolar);

System.out.println(lunar.year());            // 2024
System.out.println(lunar.month());           // 1
System.out.println(lunar.day());             // 1
System.out.println(lunar.isLeapMonth());     // false
System.out.println(lunar.toKoreanString());  // "음력 2024년 1월 1일"
```

### 2. Lunar to Solar Conversion

```java
import com.msp.solarlunar.SolarLunarConverter;
import java.time.LocalDate;
import java.util.Optional;

// Chuseok (추석, 음력 8월 15일) in 2024
Optional<LocalDate> chuseokSolar = SolarLunarConverter.toSolar(2024, 8, 15, false);
System.out.println(chuseokSolar.orElseThrow()); // 2024-09-17

// Leap Month Conversion (2023 윤2월 1일)
Optional<LocalDate> leapDate = SolarLunarConverter.toSolar(2023, 2, 1, true);
System.out.println(leapDate.orElseThrow()); // 2023-03-22
```

### 3. Astronomical Inquiries

```java
int leapMonth2023 = SolarLunarConverter.getLeapMonth(2023); // 2 (윤2월)
int leapDays2023  = SolarLunarConverter.getLeapMonthDays(2023); // 29 days
int yearDays2023  = SolarLunarConverter.getLunarYearDays(2023); // 384 days
```
