# Multi-Segment Split Stopwatch (`skill-stopwatch-split-timer`)

복합 비즈니스 로직, 배치 작업, 분산 트랜잭션의 세부 단계별(Split Lap) 소요 시간과 누적 지연, 상대적 점유율(%)을 측정하고 깔끔한 ASCII 타임라인으로 출력하는 프로파일러 모듈입니다.

## Features
- **Nano-Precision Timing**: `System.nanoTime()` 기반 초정밀 마이크로/나노초 구간 시간 측정.
- **Split Lap Profiling**: `split(lapName)` 호출만으로 이전 단계 대비 소요 시간 및 전체 누적 시간을 자동 분할 기록.
- **ASCII Report Formatter**: 콘솔 및 로그에 바로 출력 가능한 단계별 점유율(%) 표 렌더링.
- **Zero Dependencies**: 순수 Java 21 표준 라이브러리.

## Usage

```java
SplitStopwatch sw = SplitStopwatch.start("OrderCheckout");

validateCart();
sw.split("Validate Cart");

chargeCard();
sw.split("Charge PG");

sendReceipt();
sw.split("Send Email");

sw.stop();
System.out.println(sw.prettyPrint());
```
