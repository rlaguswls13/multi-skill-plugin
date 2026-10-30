# Natural Cron Parser (`skill-cron-natural-language-parser`)

한국어 및 영어 자연어 일정 표현을 표준 5-자리 유닉스 크론 표현식(`분 시 일 월 요일`)으로 변환하는 순수 Java 21 파서입니다.

## Features
- **다국어 지원**: 한국어("매일 09:00", "15분마다", "매주 월요일 14:30") 및 영어("every day at 09:00", "every 15 minutes", "every monday at 14:30") 일정 문장 완벽 지원.
- **다양한 주기 패턴**:
  - 매시간 / hourly: `0 * * * *`
  - N분마다 / every N minutes: `*/N * * * *`
  - N시간마다 / every N hours: `0 */N * * *`
  - 매일 특정 시각: `M H * * *` (자정/정오 특수 키워드 포함)
  - 주간 요일별 / 평일(1-5) / 주말(0,6): `M H * * DOW`
  - 매월 특정 일시: `M H DOM * *`
- **정밀한 유효성 검사**: 시(0-23), 분(0-59), 일(1-31), 간격 범위 벗어남 시 명확한 `IllegalArgumentException` 예외 발생.
- **Zero Runtime Dependencies**: 순수 Java 정규식 엔진(`java.util.regex.*`)만으로 동작.

## Usage

```java
import com.msp.cronlang.NaturalCronParser;

// 한국어 예시
String cron1 = NaturalCronParser.parse("매일 09:00");         // "0 9 * * *"
String cron2 = NaturalCronParser.parse("15분마다");           // "*/15 * * * *"
String cron3 = NaturalCronParser.parse("매주 월요일 14:30");  // "30 14 * * 1"
String cron4 = NaturalCronParser.parse("매주 평일 08:00");    // "0 8 * * 1-5"
String cron5 = NaturalCronParser.parse("매월 1일 00:00");     // "0 0 1 * *"

// 영어 예시
String cron6 = NaturalCronParser.parse("every hour");         // "0 * * * *"
String cron7 = NaturalCronParser.parse("every 3 hours");      // "0 */3 * * *"
String cron8 = NaturalCronParser.parse("every weekend at 10:00"); // "0 10 * * 0,6"
```
