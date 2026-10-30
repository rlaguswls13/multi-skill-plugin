# skill-object-pool-lite

무거운 바이트 버퍼, 파서, 네트워크 소켓 등 고비용 객체의 반복 생성과 GC 압박을 줄이기 위해 ConcurrentLinkedQueue와 세마포어 기반으로 구축된 고성능 무잠금(Lock-Free) 인메모리 오브젝트 풀입니다.

## 주요 기능
- `ObjectPool.builder(factory).reset(resetFn).maxSize(16).build()`: 풀 구성
- `try (Pooled<T> p = pool.borrow())`: try-with-resources 자동 반납
- `pool.withObject(obj -> ...)`: 함수형 빌림/반납 처리
- `pool.activeCount()`, `pool.idleCount()`: 모니터링 메트릭 제공
