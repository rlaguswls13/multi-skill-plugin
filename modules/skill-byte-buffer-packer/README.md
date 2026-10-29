# 📦 `skill-byte-buffer-packer`

> **외부 라이브러리 없이 Big/Little Endian 바이트 오더, 가변 길이 정수(VarInt / VarLong / ZigZag) 및 문자열 인코딩을 유연하게 처리하는 고성능 순수 Java 21 바이너리 패킷 빌더 및 파서**

- **아티팩트 좌표**: `com.msp:skill-byte-buffer-packer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-byte-buffer-packer/build/libs/skill-byte-buffer-packer-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **Fluent Binary Packet Builder (`ByteBufferPacker`)**:
   - `writeUInt8`, `writeInt8`, `writeUInt16`, `writeInt16`, `writeUInt32`, `writeInt32`, `writeInt64`, `writeFloat`, `writeDouble`, `writeBoolean`
   - `ByteOrder.BIG_ENDIAN` 및 `ByteOrder.LITTLE_ENDIAN` 완벽 지원
   - 동적 크기 자동 확장 (Auto-resizing buffer)
2. **효율적인 가변 길이 정수 압축 (LEB128 & ZigZag)**:
   - `writeVarInt` / `readVarInt`: 7비트 단위 정수 압축
   - `writeZigZagInt` / `readZigZagInt`: 음수까지 효율적으로 압축
   - `writeVarLong` / `readVarLong`, `writeZigZagLong` / `readZigZagLong`
3. **가변/고정 길이 문자열 패킹**:
   - `writeString(String)`: VarInt 길이 헤더가 붙은 UTF-8 문자열
   - `writeFixedString(String, length, charset)`: 고정 길이 패딩 문자열
4. **안전한 파서 (`ByteBufferUnpacker`)**:
   - 읽기 초과 시 상세한 `ByteBufferException` (Buffer underflow) 발생
   - `wrap(byte[])`, `wrap(ByteBuffer)` 지원
   - `position()`, `remaining()`, `skip()` 제어

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.bytepack.*;
import java.nio.ByteOrder;

// 1. 패킷 빌드 (Packer)
byte[] packet = ByteBufferPacker.allocate(ByteOrder.BIG_ENDIAN)
        .writeUInt8(0x01)                     // Magic Byte
        .writeUInt16(1001)                    // Command ID
        .writeZigZagInt(-42)                  // Compressed signed integer
        .writeString("UserLoginPacket")       // VarInt-prefixed UTF-8 string
        .toByteArray();

// 2. 패킷 파싱 (Unpacker)
ByteBufferUnpacker unpacker = ByteBufferUnpacker.wrap(packet, ByteOrder.BIG_ENDIAN);
int magic = unpacker.readUInt8();             // 1
int cmdId = unpacker.readUInt16();            // 1001
int score = unpacker.readZigZagInt();         // -42
String name = unpacker.readString();          // "UserLoginPacket"
```
