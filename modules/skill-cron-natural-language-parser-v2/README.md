# ⏰ `skill-cron-natural-language-parser-v2`

> **다국어(한국어 & 영어) 일상 자연어 문장을 표준 5필드(Linux/Unix) 및 6필드(Quartz/Spring) 크론(Cron) 표현식으로 변환하는 초경량 파서 V2**

- **아티팩트 좌표**: `com.msp:skill-cron-natural-language-parser-v2:1.0.0`
- **개별 JAR 산출물**: `modules/skill-cron-natural-language-parser-v2/build/libs/skill-cron-natural-language-parser-v2-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **다국어 자연어 패턴 분석 엔진 (Korean & English)**:
   - 한국어 구문: "매월 마지막 금요일 오후 6시", "매주 월수금 오전 9시 30분", "평일(월-금) 15분마다", "매일 밤 11시", "10분마다", "매시 30분" 등
   - 영어 구문: "every 2nd Tuesday at 3:30pm", "every 15 minutes between 9am and 5pm on weekdays", "every day at midnight", "every last Friday of the month at 6pm" 등
2. **듀얼 크론 표준 규격 동시 생성**:
   - `standardExpression()`: 표준 리눅스/유닉스 5필드 크론 (`분 시 일 월 요일`)
   - `quartzExpression()`: Quartz 및 Spring Framework 스케줄러 6필드 크론 (`초 분 시 일 월 요일`)
3. **복합 조건 및 특수 문자 파싱**:
   - N번째 요일(`#`), 월말 요일(`L`), 평일/주말 범위(`1-5`, `MON-FRI`), 시간대 범위(`9-17`) 지원
4. **Zero Dependency**:
   - 순수 Java 21 표준 라이브러리 기반 초경량 구문 분석기

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.cronlang.CronNaturalLanguageParser;
import com.msp.cronlang.ParsedCronResult;
import com.msp.cronlang.CronType;

public class CronParserExample {
    public static void main(String[] args) {
        // 1. 한국어 자연어 파싱
        ParsedCronResult r1 = CronNaturalLanguageParser.parse("매월 마지막 금요일 오후 6시");
        System.out.println("Standard 5-Field: " + r1.standardExpression()); // 0 18 * * 5L
        System.out.println("Quartz 6-Field:   " + r1.quartzExpression());   // 0 0 18 ? * 6L
        System.out.println("Description:      " + r1.humanDescription());

        // 2. 평일 주기 스케줄
        ParsedCronResult r2 = CronNaturalLanguageParser.parse("평일(월-금) 15분마다");
        System.out.println("Standard: " + r2.standardExpression()); // */15 * * * 1-5
        System.out.println("Quartz:   " + r2.quartzExpression());   // 0 */15 * ? * MON-FRI

        // 3. 영어 복합 스케줄
        ParsedCronResult r3 = CronNaturalLanguageParser.parse("every 2nd Tuesday at 3:30pm");
        System.out.println("Standard: " + r3.standardExpression()); // 30 15 * * 2#2
        System.out.println("Quartz:   " + r3.quartzExpression());   // 0 30 15 ? * 3#2
    }
}
```
