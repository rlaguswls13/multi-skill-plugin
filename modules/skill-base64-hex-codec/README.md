# 📦 `skill-base64-hex-codec`

> **순수 Java 21 표준 라이브러리 기반의 고성능 제로 할당 Base64, Base64URL, Hex, Base32(RFC 4648 & Crockford) 코덱 라이브러리**

- **아티팩트 좌표**: `com.msp:skill-base64-hex-codec:1.0.0`
- **개별 JAR 산출물**: `modules/skill-base64-hex-codec/build/libs/skill-base64-hex-codec-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **Hex (16진수)**:
   - `Codecs.toHex(bytes)`, `Codecs.toHex(bytes, uppercase)`
   - `Codecs.fromHex(hexString)` (홀수 길이 및 유효하지 않은 문자 검증)
2. **Base64 & Base64URL**:
   - `Codecs.toBase64(bytes)`, `Codecs.fromBase64(str)`
   - `Codecs.toBase64Url(bytes)` (URL 세이프, 패딩 제거 기본 지원)
   - `Codecs.fromBase64Url(str)`
3. **Base32 (RFC 4648 & Crockford)**:
   - **RFC 4648 Base32**: `Codecs.toBase32(bytes)`, `Codecs.fromBase32(str)` (대소문자 무관, 패딩 유무 유연 대응)
   - **Crockford Base32**: `Codecs.toCrockfordBase32(bytes)`, `Codecs.fromCrockfordBase32(str)` (시각적 혼동 방지 `I/L/l -> 1`, `O/o -> 0`, 하이픈 `-` 자동 허용)

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.codec.Codecs;
import java.nio.charset.StandardCharsets;

public class CodecExample {
    public static void main(String[] args) {
        byte[] data = "Hello World 2026!".getBytes(StandardCharsets.UTF_8);

        // 1. Hex
        String hex = Codecs.toHex(data);
        System.out.println("Hex: " + hex);

        // 2. Base64 & Base64URL
        String b64 = Codecs.toBase64(data);
        String b64Url = Codecs.toBase64Url(data);
        System.out.println("Base64: " + b64);
        System.out.println("Base64URL: " + b64Url);

        // 3. RFC 4648 Base32 & Crockford Base32
        String b32 = Codecs.toBase32(data);
        String crockford = Codecs.toCrockfordBase32(data);
        System.out.println("RFC32: " + b32);
        System.out.println("Crockford: " + crockford);

        // 4. 복호화
        byte[] recovered = Codecs.fromBase32(b32);
        System.out.println("Recovered: " + new String(recovered, StandardCharsets.UTF_8));
    }
}
```
