# skill-byte-sliding-window-buffer

고정된 크기 $N$의 슬라이딩 윈도우 원형 링 버퍼(Circular Ring Buffer)를 기반으로 최근 유입된 $N$바이트를 실시간 유지하고, 스트림 종단 구분자(`\r\n\r\n` 등) 및 임의의 바이트 패턴을 고속 탐색하는 순수 Java 21 유틸리티 모듈입니다.

HTTP 헤더 종단 감지, 소켓 프로토콜 프레이밍, 로그 테일 스트림 모니터링 시 매번 새로운 메모리를 할당하지 않고 $O(1)$ 시간 및 공간 복잡도로 꼬리(Tail) 바이트 시퀀스를 판별할 수 있습니다.

---

## 🚀 주요 기능

- **FIFO 슬라이딩 버퍼**: 버퍼 크기를 초과하여 바이트가 유입되면 가장 오래된 바이트가 자동으로 슬라이드 아웃
- **고속 꼬리 매칭 (`matchesTail`)**: `\r\n\r\n`과 같은 프로토콜 종단 구분자를 버퍼 복사 없이 $O(L)$ 시간으로 즉각 판정
- **원형 버퍼 패턴 검색 (`indexOf`)**: 링 버퍼의 회전 경계선(Wrap-around)을 투명하게 고려하여 임의 바이트 패턴 위치 색인
- **대용량 블록 유입 최적화**: 수용량보다 큰 바이트 배열 입력 시 불필요한 연산 없이 마지막 $N$바이트만 즉시 슬라이싱 반영
- **스레드 세이프(Thread-Safe)**: `ReentrantReadWriteLock` 기반으로 읽기/쓰기 동시성 최적화

---

## 📦 사용 예시

```java
import com.msp.slidingbytebuffer.SlidingByteBuffer;
import java.nio.charset.StandardCharsets;

public class SlidingBufferExample {
    public static void main(String[] args) {
        // 최근 64바이트를 유지하는 슬라이딩 윈도우 버퍼
        SlidingByteBuffer buffer = new SlidingByteBuffer(64);
        byte[] CRLF2 = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

        // 스트림 데이터 수신
        buffer.append("GET /api/v1/resource HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        buffer.append("Host: api.example.com\r\n".getBytes(StandardCharsets.US_ASCII));

        System.out.println("Header completed? " + buffer.matchesTail(CRLF2)); // false

        // 종단 줄바꿈 도착
        buffer.append("\r\n".getBytes(StandardCharsets.US_ASCII));
        System.out.println("Header completed? " + buffer.matchesTail(CRLF2)); // true

        // 패턴 탐색
        int hostIdx = buffer.indexOf("Host:".getBytes(StandardCharsets.US_ASCII));
        System.out.println("Offset of 'Host:': " + hostIdx);
    }
}
```

---

## 🧪 테스트 검증 매트릭스

- `testCapacityValidation`: 양수 수용량 유효성 검증
- `testFifoSliding`: 용량 초과 시 오래된 바이트 FIFO 제거 순서 검증
- `testMultiByteAppend`: 바이트 배열 슬라이스(`offset`, `length`) 추가 검증
- `testAppendLargerThanCapacity`: 버퍼 크기 초과 입력 시 마지막 $N$바이트 잔존 검증
- `testMatchesTailHttpHeader`: HTTP 헤더 종단자 `\r\n\r\n` 판별 및 후속 바이트 입력 시 불일치 전환 검증
- `testIndexOf`: 링 버퍼 경계 wrap-around 패턴 검색 및 미일치(-1) 반환 검증
- `testClear`: 버퍼 초기화 검증
- `testConcurrentAccess`: 다중 스레드 환경 동시 추가 및 읽기 안전성 검증
