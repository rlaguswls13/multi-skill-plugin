# 📦 `skill-cron-scheduler-lite`

> **외부 프레임워크(Quartz 등) 의존성 없는 순수 Java 21 경량 크론 표현식 파서 및 인메모리 스케줄러 (`CronExpression`, `LightweightCronScheduler`, `Cron`)**

- **아티팩트 좌표**: `com.msp:skill-cron-scheduler-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-cron-scheduler-lite/build/libs/skill-cron-scheduler-lite-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **표준 크론 파싱**: 5필드(Unix: 분, 시, 일, 월, 요일) 및 6필드(Quartz: 초, 분, 시, 일, 월, 요일) 완벽 지원
2. **다양한 표현식 연산자**: `*`, `?`, `,` (목록), `-` (범위), `/` (스텝 증분), 영문 요일(MON-SUN) 및 월(JAN-DEC)
3. **정확한 다음 실행 시간 계산**: `next(ZonedDateTime from)`, `next(from, count)` (윤년 2월 29일, 월말 전이 완벽 대응)
4. **인메모리 경량 스케줄러 (`LightweightCronScheduler`)**: `ScheduledExecutorService` 기반 비동기 주기 실행, 작업 개별 취소(`task.cancel()`) 및 수명주기 제어

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.cron.*;
import java.time.ZonedDateTime;
import java.util.List;

public class CronExample {
    public static void main(String[] args) throws Exception {
        // 1. 크론 표현식 파싱 및 다음 실행 시점 계산
        CronExpression cron = CronExpression.parse("0 30 9 * * MON-FRI"); // 평일 09:30:00
        ZonedDateTime now = ZonedDateTime.now();

        ZonedDateTime nextExecution = cron.next(now);
        System.out.println("다음 실행 시점: " + nextExecution);

        List<ZonedDateTime> nextFive = cron.next(now, 5);
        nextFive.forEach(t -> System.out.println("예정 일정: " + t));

        // 2. 인메모리 태스크 스케줄링
        LightweightCronScheduler scheduler = LightweightCronScheduler.create();

        ScheduledTask task = scheduler.schedule("*/5 * * * * *", () -> {
            System.out.println("5초마다 실행되는 헬스체크: " + ZonedDateTime.now());
        });

        Thread.sleep(12000); // 12초 후 작업 취소
        task.cancel();
        scheduler.shutdown();
    }
}
```
