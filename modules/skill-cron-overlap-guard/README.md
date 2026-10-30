# skill-cron-overlap-guard

배치 및 Cron 스케줄러 작업의 실행 시간이 스케줄 주기보다 길어질 때, 이전 작업이 끝나지 않았으면 다음 실행을 안전하게 스킵(Skip)하고 실행/스킵 통계를 추적하는 비재진입(Non-reentrant) 안전 가드 모듈입니다.

## 주요 기능
- `CronOverlapGuard.of("task-name")`: 작업별 가드 인스턴스 생성
- `guard.run(task)` / `guard.call(task)`: 비재진입 안전 실행 및 결과/스킵 상태 반환
- `guard.executedCount()` / `guard.skippedCount()`: 실행 및 스킵 카운트 메트릭 제공
