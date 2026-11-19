# skill-byte-msgpack-lite-codec

> Pure Java MessagePack binary serialization and deserialization codec complying with the official MessagePack specification with zero external dependencies.

---

## 🚀 Features

- **Standard MessagePack Format Coverage**:
  - **Integers**: Positive / Negative fixint, uint8 / 16 / 32 / 64, int8 / 16 / 32 / 64.
  - **Floating Point**: Float 32 (IEEE 754 single precision) & Float 64 (IEEE 754 double precision).
  - **Strings**: fixstr (0–31 bytes), str8 (up to 255 bytes), str16 (up to 64KB), str32 (large UTF-8 strings). Full multi-byte UTF-8 and emoji support.
  - **Binary**: bin8, bin16, bin32 raw byte arrays (`byte[]`).
  - **Collections**: fixarray, array16, array32 (`List<?>`, arrays).
  - **Maps**: fixmap, map16, map32 (`Map<?, ?>`).
  - **Booleans & Nil**: `null`, `true`, `false`.
- **High-Performance & Lightweight**:
  - Implemented strictly with standard Java I/O (`DataInputStream`, `DataOutputStream`, `ByteArrayOutputStream`).
  - No external Jackson or msgpack-core JAR runtime dependencies.
- **Pure Java 21**: Zero 3rd-party dependencies.

---

## 📦 Usage Examples

### 1. Encode and Decode Primitives & Maps

```java
import com.msp.msgpack.MessagePackCodec;
import java.util.Map;
import java.util.List;

Map<String, Object> data = Map.of(
    "service", "auth-gateway",
    "port", 8080L,
    "enabled", true,
    "tags", List.of("security", "oauth2")
);

// Serialize to MessagePack byte[]
byte[] msgpackBytes = MessagePackCodec.encode(data);

// Deserialize back to Java Map
Map<String, Object> decoded = MessagePackCodec.decodeMap(msgpackBytes);
System.out.println(decoded.get("service")); // "auth-gateway"
```

### 2. Binary and Stream Support

```java
byte[] payload = new byte[]{0x01, 0x02, (byte) 0xff};
byte[] packed = MessagePackCodec.encode(payload);

byte[] restored = (byte[]) MessagePackCodec.decode(packed);
```

---

## 🧪 Testing

```bash
./gradlew :modules:skill-byte-msgpack-lite-codec:test
```
