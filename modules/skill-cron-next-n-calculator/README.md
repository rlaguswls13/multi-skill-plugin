# skill-cron-next-n-calculator

표준 5필드 Cron 표현식(분, 시, 일, 월, 요일)을 기반으로, 특정 기준 일시 이후의 차기 $N$회차 실행 시각(`List<ZonedDateTime>`)을 타임존별로 빠르고 정확하게 계산하는 단일 기술 모듈입니다.

## 주요 기능
- **표준 5필드 지원**: 분(`0-59`), 시(`0-23`), 일(`1-31`), 월(`1-12`), 요일(`0-7`, `0`/`7`=일요일).
- **특수 기호 완벽 지원**: 와일드카드(`*`), 목록(`,`), 범위(`-`), 증분/스텝(`/`).
- **타임존 지원**: `ZoneId`가 포함된 `ZonedDateTime` 기반 시간 연산.
- **다회차 계산**: `nextN(cron, from, count)`으로 향후 $N$건의 실행 타임라인 생성.
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(`java.time.*`) 기반.

## 사용 예제

```java
import com.msp.cronnext.CronNextCalculator;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        // 매 15분마다 실행되는 크론의 차기 5회 실행 시각 계산
        List<ZonedDateTime> nextRuns = CronNextCalculator.nextN("*/15 * * * *", now, 5);
        for (ZonedDateTime run : nextRuns) {
            System.out.println("Next run: " + run);
        }
    }
}
```
