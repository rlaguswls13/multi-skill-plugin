# 📦 `skill-http-sse-client-reconnector`

> **순수 Java 21 표준 라이브러리(`java.net.http.HttpClient`) 기반 자동 재연결, 지수 백오프 및 `Last-Event-ID` 추적 지원 SSE(Server-Sent Events) 클라이언트**

- **아티팩트 좌표**: `com.msp:skill-http-sse-client-reconnector:1.0.0`
- **개별 JAR 산출물**: `modules/skill-http-sse-client-reconnector/build/libs/skill-http-sse-client-reconnector-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **WHATWG SSE 규격 파서 (`SseEventParser`, `ServerSentEvent`)**:
   - `id:`, `event:`, `data:`, `retry:`, 콜론 주석(`:ping`) 및 복수 줄 `data:` 자동 연결 지원.
   - 콜론 뒤 첫 공백 제거 등 SSE 표준 스펙 완벽 준수.
2. **`Last-Event-ID` 자동 추적 및 헤더 재전송**:
   - 수신된 이벤트의 `id`를 보관하며, 연결 끊김 후 재연결 시 `Last-Event-ID` 요청 헤더를 서버에 자동 전송하여 유실 없는 스트림 복구.
3. **지터(Jitter)가 적용된 지수 백오프 (`BackoffPolicy`)**:
   - 서버 폭주(Thundering Herd)를 방지하는 무작위 지터 기반 지수 재시도.
   - 서버의 `retry: <ms>` 지시어가 수신될 경우 재연결 기본 지연시간으로 자동 반영.
4. **비동기 및 동기 연결 지원 (`AutoCloseable`)**:
   - `connectAsync()` 및 `connect()` 지원, `try-with-resources` 또는 `close()`로 안전한 자원 해제.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.sseclient.*;
import java.net.URI;
import java.time.Duration;

public class SseClientExample {
    public static void main(String[] args) throws Exception {
        URI sseUri = URI.create("https://api.example.com/v1/stream");

        SseClient client = SseClient.builder(sseUri)
                .header("Authorization", "Bearer eyJhbGciOi...")
                .backoffPolicy(new BackoffPolicy(
                        Duration.ofMillis(1000), // 초기 지연
                        Duration.ofMillis(30000), // 최대 지연
                        2.0,                     // 지수 배수
                        0.2                      // 20% 지터
                ))
                .autoReconnect(true)
                .maxReconnectAttempts(10)
                .listener(new SseEventListener() {
                    @Override
                    public void onOpen() {
                        System.out.println("SSE Stream Opened!");
                    }

                    @Override
                    public void onEvent(ServerSentEvent event) {
                        System.out.printf("[%s] ID=%s Data=%s%n",
                                event.event(), event.id(), event.data());
                    }

                    @Override
                    public void onComment(String comment) {
                        System.out.println("Keepalive Ping: " + comment);
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.err.println("SSE Error: " + t.getMessage());
                    }

                    @Override
                    public void onClose() {
                        System.out.println("SSE Client Closed.");
                    }
                })
                .build();

        // 비동기 연결 시작
        client.connectAsync();

        // 스트림 사용 후 안전하게 종료
        Thread.sleep(10000);
        client.close();
    }
}
```
