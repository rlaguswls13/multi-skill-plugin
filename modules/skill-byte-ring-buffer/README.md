# Zero-GC High-Performance Circular Byte Ring Buffer (`skill-byte-ring-buffer`)

네트워크 소켓 I/O, 패킷 프레이밍, 실시간 오디오/비디오 스트리밍, 스레드 간 생산자-소비자(Producer-Consumer) 데이터 전달을 위해 설계된 고정 크기 원형 바이트 버퍼(Circular Byte Buffer)입니다.

읽기 및 쓰기 시 일체의 힙 메모리 객체 할당(Zero GC Allocation) 없이 `System.arraycopy` 기반으로 인메모리 순환 포인터를 조작하여 극도로 낮은 레이턴시와 높은 처리량을 보장합니다.

## Features

- **Zero-GC 메모리 아키텍처**:
  - 생성 시 1회 사전 할당된 바이트 배열(`byte[]`)만을 재사용하여 가비지 컬렉션(GC) 부하 0%
  - 순환 래핑(Circular Wrap-around) 구간을 분할 복사하여 최대 2회의 `System.arraycopy`로 단일 청크 처리
- **완전한 I/O API 제공**:
  - `write(byte[] src, int offset, int length)` / `writeByte(byte b)`
  - `read(byte[] dst, int offset, int length)` / `readByte()`
  - `peek()` / `peek(byte[] dst, int offset, int length)` (버퍼 소진 없는 미리보기)
  - `skip(int bytesToSkip)` (불필요한 헤더/패딩 고속 폐기)
  - `availableRead()`, `availableWrite()`, `isFull()`, `isEmpty()`, `clear()`
- **동시성 안전성 (Thread-Safe)**:
  - 모니터 락(`synchronized`)을 통한 안전한 다중 스레드 I/O 처리
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리만으로 동작

## Usage

```java
import com.msp.ringbuffer.ByteRingBuffer;

// 1. 1024바이트 고정 크기 링 버퍼 생성
ByteRingBuffer ring = new ByteRingBuffer(1024);

// 2. 바이트 데이터 쓰기
byte[] data = "Hello, Network Stream!".getBytes();
int written = ring.write(data);

// 3. 버퍼 상태 점검
System.out.println("Available to read: " + ring.availableRead());
System.out.println("Available to write: " + ring.availableWrite());

// 4. 데이터 미리보기 (Peek)
byte firstByte = ring.peek(); // 'H'

// 5. 바이트 데이터 읽기
byte[] buffer = new byte[64];
int read = ring.read(buffer, 0, ring.availableRead());
String received = new String(buffer, 0, read);
System.out.println("Received: " + received);
```
