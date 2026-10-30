# skill-snowflake-worker-id

분산 환경에서 ZooKeeper/Redis 없이 호스트 IP, MAC 주소, PID 비트패킹을 통해 충돌 없는 Snowflake Worker ID(0~1023)를 자동 할당하고 유효성을 검증하는 단일 기술 모듈입니다.

## 주요 기능
- `WorkerIdGenerator.autoAssign(1023)`: PID+IP -> MAC -> Host IP -> SecureRandom 폴백 자동 프로비저닝
- `WorkerIdValidator.validate(workerId, 1023)`: 범위 유효성 검사

## 입력/출력 매칭 예시
| 입력 메서드 | 파라미터 | 결과 |
| --- | --- | --- |
| `autoAssign(1023)` | `maxWorkerId = 1023` | `0 ~ 1023` 사이의 정수 (예: `42`) |
| `isValid(42, 1023)` | `workerId = 42, max = 1023` | `true` |
