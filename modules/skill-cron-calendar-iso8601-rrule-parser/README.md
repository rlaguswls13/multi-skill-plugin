# 📦 `skill-cron-calendar-iso8601-rrule-parser`

> **iCalendar RFC 5545 Recurrence Rule (RRULE) Parser and Date Range Occurrence Evaluator in Pure Java 21**

- **아티팩트 좌표**: `com.msp:skill-cron-calendar-iso8601-rrule-parser:1.0.0`
- **개별 JAR 산출물**: `modules/skill-cron-calendar-iso8601-rrule-parser/build/libs/skill-cron-calendar-iso8601-rrule-parser-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21 `java.time.*`)

---

## 1. 주요 기능 (Features)

1. **RFC 5545 iCalendar RRULE 표준 파싱**:
   - `FREQ` (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`)
   - `INTERVAL` (양의 정수 주기)
   - `COUNT` (총 반복 발생 횟수 상한)
   - `UNTIL` (반복 종료 시점, RFC 5545 Basic `yyyyMMdd'T'HHmmss'Z'` 및 ISO-8601 지원)
   - `BYDAY` (요일 및 순번 접두사: `MO,WE,FR`, `1MO` 첫째주 월요일, `-1FR` 마지막주 금요일)
   - `BYMONTH`, `BYMONTHDAY` (음수 역순 포함: -1 마지막 날), `BYSETPOS`
   - `WKST` (한 주의 시작 요일, 기본값 `MO`)
2. **지연 생성 반복 평가기 (`OccurrenceIterator`)**:
   - `iterator(ZonedDateTime start)`를 통한 메모리 절약형 Lazy 스트리밍
3. **구간 필터링 및 최대 건수 제한**:
   - `getOccurrences(start, from, to, maxResults)`: 특정 조회 윈도우 [from, to] 내의 발생 일정만 정확하게 추출
   - 윤달, 월말 일수 차이(28/29/30/31일), 타임존 보존 지원
4. **타입 세이프 Fluent Builder**:
   - `RecurrenceRule.builder().frequency(Frequency.WEEKLY).interval(2)...build()`

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.rrule.RecurrenceRule;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

public class RRuleExample {
    public static void main(String[] args) {
        // 1. 매월 마지막 금요일에 실행되는 반복 룰 파싱
        String rruleStr = "RRULE:FREQ=MONTHLY;BYDAY=FR;BYSETPOS=-1;COUNT=12";
        RecurrenceRule rule = RecurrenceRule.parse(rruleStr);

        // 2. 기준 시작 일시 설정 (2026-01-01 10:00 UTC)
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

        // 3. 2026년 하반기 (7월 ~ 12월) 발생 일정 추출
        ZonedDateTime from = ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime to = ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

        List<ZonedDateTime> occurrences = rule.getOccurrences(start, from, to, 10);
        for (ZonedDateTime dt : occurrences) {
            System.out.println("Occurrence: " + dt);
        }
    }
}
```
