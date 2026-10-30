# skill-cron-jitter-randomizer

대규모 분산 배치 작업이 동일 정각에 일제히 집중되어 DB 및 API에 부하를 유발하는 Thundering Herd(스탬피드)를 방지하기 위해 Cron 실행 시각에 난수 또는 키 기반 고정 지터(Jitter: ±N초/분)를 가산하는 단일 기술 모듈입니다.

## 주요 기능
- `CronJitter.applyRandomJitter(time, maxJitter)`: `[0, maxJitter]` 무작위 지터 가산
- `CronJitter.applySymmetricJitter(time, maxJitter)`: `[-maxJitter, +maxJitter]` 대칭 지터 가산
- `CronJitter.applyDeterministicJitter(time, key, maxJitter)`: 서버/테넌트 키 기반 결정론적 지터
