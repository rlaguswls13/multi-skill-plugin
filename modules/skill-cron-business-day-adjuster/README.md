# 📅 `skill-cron-business-day-adjuster`

> **순수 Java 21 표준 라이브러리 기반 주말 및 공휴일 연동 Cron 영업일(Business Day) 스케줄 조정기**

- **아티팩트 좌표**: `com.msp:skill-cron-business-day-adjuster:1.0.0`
- **개별 JAR 산출물**: `modules/skill-cron-business-day-adjuster/build/libs/skill-cron-business-day-adjuster-1.0.0.jar`
- **외부 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 특징 (Features)

1. **3가지 영업일 롤오버 정책 (`BusinessDayRollPolicy`)**:
   - `ROLL_FORWARD`: 예정일이 휴일/주말일 경우 직후 첫 번째 도래하는 영업일로 순연
   - `ROLL_BACKWARD`: 직전 가장 가까운 영업일로 당겨서 실행
   - `SKIP`: 휴일/주말인 경우 당회 실행을 안전하게 취소/생략 (`Optional.empty()`)
2. **시간 및 타임존 완벽 보존**:
   - `ZonedDateTime` 및 `LocalDateTime`의 실행 시각(시, 분, 초, 나노초)과 `ZoneId`를 그대로 보존하며 일자만 정확히 조정
3. **연속 휴일 체인 및 윤년 완벽 대응**:
   - 징검다리 연휴, 대체공휴일, 신정/구정 등 다중 연속 휴일 구간 자동 건너뛰기
   - 2월 29일 윤일(Leap day) 및 윤년 정확한 날짜 계산
4. **결합도 제로 & 불변 스레드 세이프**:
   - Java 표준 `java.time` 기반 순수 구현으로 모든 메서드가 thread-safe하며 부작용(side-effect) 없음

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.cronbiz.BusinessDayRollPolicy;
import com.msp.cronbiz.CronBusinessDayAdjuster;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

public class CronBusinessDayExample {
    public static void main(String[] args) {
        // 공휴일 등록 (예: 성탄절 12월 25일)
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 12, 25));
        CronBusinessDayAdjuster adjuster = new CronBusinessDayAdjuster(holidays);

        // 일요일 오전 9시 스케줄링된 작업
        ZonedDateTime scheduledSunday = ZonedDateTime.of(
                LocalDate.of(2026, 11, 8),
                LocalTime.of(9, 0),
                ZoneId.of("Asia/Seoul")
        );

        // 1. ROLL_FORWARD: 다음 영업일(월요일 11월 9일 09:00)로 조정
        Optional<ZonedDateTime> forwardTime = adjuster.adjust(scheduledSunday, BusinessDayRollPolicy.ROLL_FORWARD);
        forwardTime.ifPresent(t -> System.out.println("Roll Forward: " + t));

        // 2. ROLL_BACKWARD: 직전 영업일(금요일 11월 6일 09:00)로 조정
        Optional<ZonedDateTime> backwardTime = adjuster.adjust(scheduledSunday, BusinessDayRollPolicy.ROLL_BACKWARD);
        backwardTime.ifPresent(t -> System.out.println("Roll Backward: " + t));

        // 3. SKIP: 휴일 실행 취소
        Optional<ZonedDateTime> skippedTime = adjuster.adjust(scheduledSunday, BusinessDayRollPolicy.SKIP);
        System.out.println("Is Skipped: " + skippedTime.isEmpty()); // true
    }
}
```
