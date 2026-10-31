# 📦 `skill-cron-execution-frequency-analyzer`

> **순수 Java 21 표준 라이브러리 기반 Cron 표현식 연간/월간/일간 총 실행 횟수 계산 및 피크 타임 히스토그램 분석기**

- **아티팩트 좌표**: `com.msp:skill-cron-execution-frequency-analyzer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-cron-execution-frequency-analyzer/build/libs/skill-cron-execution-frequency-analyzer-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **표준 5필드 Cron 표현식 완벽 해석**:
   - `minute` (0-59), `hour` (0-23), `dayOfMonth` (1-31), `month` (1-12 또는 JAN-DEC), `dayOfWeek` (0-7 또는 SUN-SAT)
   - 와일드카드(`*`), 목록(`,`), 범위(`-`), 주기 증분(`/`), 물음표(`?`) 및 표준 매크로(`@daily`, `@hourly`, `@monthly` 등) 지원
2. **연간/월간/주간/일간 실행 빈도 예측 (`CronFrequencyReport`)**:
   - 표준 참조 연도 시뮬레이션을 통해 1년 총 실행 횟수 및 기간별 평균 실행 빈도를 정확하게 계산
3. **피크 타임(Peak Hour) 및 시간대/요일별 분포 히스토그램**:
   - 24시간 시간대별 실행 분포(`hourlyHistogram`) 및 요일별 실행 분포(`dowHistogram`) 산출
   - 하루 중 가장 부하가 집중되는 피크 시간대(`getPeakHour`) 자동 감지
4. **터미널 친화적 ASCII 히스토그램 차트 출력 (`toAsciiHourlyChart`)**:
   - 콘솔 로그 및 CLI 도구에서 직관적으로 부하 집중도를 확인할 수 있는 수평 막대 차트 생성

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.cronfreq.CronFrequencyAnalyzer;
import com.msp.cronfreq.CronFrequencyReport;

public class CronFreqExample {
    public static void main(String[] args) {
        // 평일 오전 9시부터 오후 6시까지 30분 간격 실행
        String cron = "*/30 9-18 * * MON-FRI";

        // 1. 실행 빈도 분석
        CronFrequencyReport report = CronFrequencyAnalyzer.analyze(cron);
        System.out.printf("Yearly: %,d runs%n", report.yearlyCount());
        System.out.printf("Monthly: %,d runs%n", report.monthlyCount());
        System.out.printf("Weekly: %,d runs%n", report.weeklyCount());
        System.out.printf("Daily: %,d runs%n", report.dailyCount());

        // 2. 피크 시간대 탐색
        int peakHour = CronFrequencyAnalyzer.getPeakHour(cron);
        System.out.println("Peak Hour: " + peakHour + ":00");

        // 3. ASCII 막대 차트 출력
        String chart = CronFrequencyAnalyzer.toAsciiHourlyChart(cron);
        System.out.println(chart);
    }
}
```
