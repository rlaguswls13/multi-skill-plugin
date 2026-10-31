# Cron Diff Comparator (`skill-cron-diff-comparator`)

두 Cron 표현식(Cron A, Cron B) 간의 주기 차이, 겹치는 시간대(Overlap / Collision) 및 차기 N회차 일치율(Jaccard Similarity)을 비교 분석하는 모듈입니다.
배치 스케줄링 간의 리소스 경합 방지, 중복 작업 탐지, 스케줄 리팩토링 검증에 사용됩니다.

## 특징
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(`BitSet`, `java.time`)만으로 구현
- **5자리 표준 Cron 지원**: `minute hour day-of-month month day-of-week`
- **풍부한 문법 지원**: 와일드카드(`*`), 목록(`,`), 범위(`-`), 증분/스텝(`/`), 무시(`?`)
- **교집합 및 여집합 분석**:
  - `bothMatchTimes`: 두 스케줄이 동시에 발화하는 시간 (리소스 충돌 후보)
  - `onlyAMatchTimes`: 스케줄 A에서만 발화하는 시간
  - `onlyBMatchTimes`: 스케줄 B에서만 발화하는 시간
- **일치율(Overlap Percentage) 산출**: 자카드 유사도(|A ∩ B| / |A ∪ B|) 기반 백분율 제공
- **시간 윈도우 및 N회차 시뮬레이션 지원**: 특정 날짜 구간(`compare`) 또는 차기 N회 발생(`compareNextOccurrences`) 비교 가능

## 사용법

```java
import com.msp.crondiff.CronDiffComparator;
import com.msp.crondiff.CronDiffResult;
import java.time.LocalDateTime;

// 1. 비교 대상 Cron 표현식
String cronA = "*/10 * * * *"; // 10분마다 실행
String cronB = "*/5 * * * *";  // 5분마다 실행

// 2. 특정 기간(start ~ end) 동안의 비교 분석
LocalDateTime start = LocalDateTime.now();
LocalDateTime end = start.plusDays(1);

CronDiffResult result = CronDiffComparator.compare(cronA, cronB, start, end);

// 3. 분석 결과 확인
System.out.println("일치율: " + result.overlapPercentage() + "%"); // 50.0%
System.out.println("동시 발화 횟수: " + result.bothMatchTimes().size());
System.out.println("B 단독 발화 횟수: " + result.onlyBMatchTimes().size());

// 4. 텍스트 보고서 출력
System.out.println(result.summary());
```
