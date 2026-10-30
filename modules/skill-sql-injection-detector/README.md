# skill-sql-injection-detector

동적 SQL 파라미터 또는 사용자 쿼리 입력에서 Tautology(`' OR '1'='1`), UNION SELECT, Stacked Queries(`; DROP TABLE`), 주석 인젝션(`--`, `/* */`), 명령어 실행(`xp_cmdshell`) 등의 SQL Injection 공격 패턴을 사전에 탐지하고 차단하는 보안 검사기 모듈입니다.

## 주요 기능
- `SqlInjectionDetector.detect(input)`: 공격 패턴 매칭 및 세부 룰 정보 반환
- `SqlInjectionDetector.isSafe(input)`: Boolean 안전 여부 검사
- `SqlInjectionDetector.assertSafe(input)`: 위험 패턴 감지 시 `SqlInjectionException` 즉시 발생
