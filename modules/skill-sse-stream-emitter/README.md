# skill-sse-stream-emitter

W3C Server-Sent Events (SSE) 명세에 따른 이벤트 와이어 포맷 직렬화(`id`, `event`, `data`, `retry`, `comment`), 멀티라인 데이터 인코딩, 다중 클라이언트 브로드캐스터 및 킵얼라이브(Ping) 기능을 제공하는 경량 단일 기술 모듈입니다.

## 주요 기능
- `SseEvent.builder().event("notice").data("message").build().format()`: W3C SSE 표준 포맷팅
- `SseBroadcaster`: 인메모리 클라이언트 구독 관리, 이벤트 브로드캐스트, 유휴 연결 방지 `ping()`
