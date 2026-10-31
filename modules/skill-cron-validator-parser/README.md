# ⏱️ skill-cron-validator-parser

5필드(Unix Standard) 및 6필드(Spring/Quartz 초 포함) Cron 표현식의 문법 유효성 정밀 검증 및 자연어 설명 분해기 라이브러리입니다.

## ✨ 주요 기능
- **5필드 & 6필드 다형성 지원**: 표준 Unix(분-시-일-월-요일) 및 초 필드가 포함된 스프링/Quartz 포맷 동시 인식
- **특수 기호 정밀 검증**: `*`, `*/n` (step), `n-m` (range), `n,m` (list), `?` (no specific), `L` (last), `W` (nearest weekday), `#` (nth weekday)
- **영문 약어 인식**: `JAN-DEC`, `SUN-SAT`, `MON-FRI` 등 대소문자 무관 직관적 표현 완벽 파싱
- **자연어 텍스트 요약 (`describe`)**: `0 0 12 * * ?` -> `"At 12:00:00 every day"` 등 읽기 쉬운 요약 문장 생성
- **Pure Java 21**: 외부 런타임 3rd-party 의존성 Zero

---

## 🚀 빠른 시작 (Quick Start)

### 1. 문법 검증 및 필드별 분해
```java
import com.msp.cronvalidator.CronValidator;
import com.msp.cronvalidator.CronValidationResult;

public class Example {
    public static void main(String[] args) {
        String cron = "0 15 10 ? * 6L"; // 매월 마지막 금요일 10:15
        CronValidationResult result = CronValidator.validate(cron);

        if (result.valid()) {
            System.out.println("Cron expression is valid!");
            result.fields().forEach(field -> {
                System.out.printf("[%s] %s -> %s\n",
                        field.type(), field.expression(), field.description());
            });
        }
    }
}
```

### 2. 자연어 설명 생성
```java
System.out.println(CronValidator.describe("*/15 * * * *"));
// 출력: "Every 15 minutes"

System.out.println(CronValidator.describe("0 0 9 * * MON-FRI"));
// 출력: "At 09:00, only on Monday through Friday"
```
