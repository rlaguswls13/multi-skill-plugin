# skill-retry-exhausted-fallback

재시도 및 서킷 브레이커 최종 실패 시 캐시 폴백, 대체값 주입, 인메모리 Dead-Letter Queue(DLQ)에 실패 페이로드 및 예외 원인을 자동 격리하는 안전 복구 파이프라인 단일 기술 모듈입니다.

## 주요 기능
- `FallbackPipeline.of(task).fallbackValue(defaultVal).execute()`: 기본값 폴백
- `FallbackPipeline.of(task).fallbackFunction(ex -> ...).execute()`: 예외 동적 처리
- `FallbackPipeline.of(task).deadLetterQueue(dlq, id).execute()`: 실패 작업 DLQ 자동 보관
- `dlq.drainAll()` / `dlq.snapshot()`: 격리된 실패 항목 추출 및 후속 분석
