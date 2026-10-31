# 📦 `skill-base58-bitcoin-codec`

> **순수 Java 21 표준 라이브러리 기반 비트코인 및 IPFS 표준 Base58 및 Base58Check(Double SHA-256 4바이트 체크섬) 코덱**

- **아티팩트 좌표**: `com.msp:skill-base58-bitcoin-codec:1.0.0`
- **개별 JAR 산출물**: `modules/skill-base58-bitcoin-codec/build/libs/skill-base58-bitcoin-codec-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **표준 Base58 인코딩 & 디코딩**:
   - 시각적으로 혼동하기 쉬운 문자(`0`, `O`, `I`, `l`)를 제외한 비트코인/IPFS 표준 58자 알파벳 사용
   - 입력 바이트 앞단의 `0x00`(Zero byte)을 Base58 선행 `'1'` 문자로 완벽 보존
2. **Base58Check (Bitcoin Checksum) 지원**:
   - `[1-byte Version] + [Payload] + [4-byte Double SHA-256 Checksum]` 포맷
   - 인코딩 시 Double SHA-256 상위 4바이트 자동 산출 및 부착
   - 디코딩 시 체크섬 불일치, 데이터 변조 및 길이 부족 자동 검증 및 예외 발생

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.base58.Base58Codec;
import com.msp.base58.Base58CheckPayload;
import java.nio.charset.StandardCharsets;

public class Base58Example {
    public static void main(String[] args) {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);

        // 1. 표준 Base58 인코딩
        String encoded = Base58Codec.encode(data);
        System.out.println("Base58: " + encoded); // StV1DL6CwTryKyV

        // 2. Base58 디코딩
        byte[] decoded = Base58Codec.decode(encoded);
        System.out.println("Decoded: " + new String(decoded, StandardCharsets.UTF_8));

        // 3. Base58Check 인코딩 (Version 0x00: Bitcoin Mainnet P2PKH)
        int version = 0x00;
        String checked = Base58Codec.encodeChecked(version, data);
        System.out.println("Base58Check: " + checked);

        // 4. Base58Check 디코딩 & 체크섬 검증
        byte[] payloadOnly = Base58Codec.decodeChecked(checked);
        Base58CheckPayload full = Base58Codec.decodeCheckedWithVersion(checked);
        System.out.println("Version: " + full.version());
        System.out.println("Payload: " + new String(full.payload(), StandardCharsets.UTF_8));
    }
}
```
