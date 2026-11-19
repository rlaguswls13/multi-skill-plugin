# Korean Lunar Holiday & Cron Schedule Adjuster (`skill-cron-holiday-kr-lunar-calendar`)

Pure Java 21 astronomical lunar-to-solar converter for major Korean public holidays (설날, 추석, 부처님오신날), official substitute holiday (대체공휴일) calculator, and Cron schedule business day adjuster.

## Features

- **Accurate Korean Lunar Holiday Conversion**: Exact solar conversion tables covering years 2015 to 2035 for:
  - **설날 (Lunar New Year)**: Lunar 12/last day, 1/1, 1/2 (3 consecutive days).
  - **추석 (Chuseok)**: Lunar 8/14, 8/15, 8/16 (3 consecutive days).
  - **부처님오신날 (Buddha's Birthday)**: Lunar 4/8.
- **Fixed Solar Holidays**: Covers 신정 (1/1), 삼일절 (3/1), 어린이날 (5/5), 현충일 (6/6), 광복절 (8/15), 개천절 (10/3), 한글날 (10/9), 성탄절 (12/25).
- **Official Substitute Holiday (대체공휴일) Rules**: Full compliance with Presidential Decree on Public Holidays (관공서의 공휴일에 관한 규정) including latest amendments for 부처님오신날 and 성탄절.
- **Cron / Schedule Adjustment Policies**:
  - `ROLL_FORWARD`: Automatically shifts to the next open business day.
  - `ROLL_BACKWARD`: Shifts backward to the preceding business day.
  - `SKIP`: Discards execution when falling on a holiday or weekend.
- **Pure Java 21**: Zero 3rd party runtime dependencies.

## Runnable Code Example

```java
import com.msp.lunarholiday.AdjustmentPolicy;
import com.msp.lunarholiday.KoreanHoliday;
import com.msp.lunarholiday.KoreanHolidayCalendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class HolidayExample {
    public static void main(String[] args) {
        KoreanHolidayCalendar calendar = KoreanHolidayCalendar.getInstance();

        // 1. Inspect holidays for year 2024
        List<KoreanHoliday> holidays2024 = calendar.getHolidaysForYear(2024);
        for (KoreanHoliday h : holidays2024) {
            System.out.printf("[%s] %s (%s)%n", h.date(), h.name(), h.type());
        }

        // 2. Query holiday status
        LocalDate seollal2024 = LocalDate.of(2024, 2, 10);
        System.out.println("Is 2024-02-10 holiday: " + calendar.isHoliday(seollal2024)); // true

        // 3. Check substitute holiday
        LocalDate substituteDay = LocalDate.of(2024, 2, 12);
        Optional<KoreanHoliday> sub = calendar.getHoliday(substituteDay);
        sub.ifPresent(h -> System.out.println("Substitute Holiday: " + h.name()));

        // 4. Adjust Cron execution scheduled on a holiday
        LocalDate scheduled = LocalDate.of(2024, 2, 10); // Saturday & Seollal
        LocalDate adjustedForward = calendar.adjust(scheduled, AdjustmentPolicy.ROLL_FORWARD);
        System.out.println("Adjusted Forward: " + adjustedForward); // 2024-02-13 (Tuesday)

        LocalDate adjustedBackward = calendar.adjust(scheduled, AdjustmentPolicy.ROLL_BACKWARD);
        System.out.println("Adjusted Backward: " + adjustedBackward); // 2024-02-08 (Thursday)

        // 5. Query next 3 business days
        List<LocalDate> nextBizDays = calendar.nextBusinessDays(LocalDate.of(2024, 2, 8), 3);
        System.out.println("Next 3 business days: " + nextBizDays);
    }
}
```
