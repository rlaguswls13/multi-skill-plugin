# 📦 `skill-base32-hex-crockford-codec`

> **순수 Java 21 표준 라이브러리 기반 RFC 4648 표준 Base32, Extended Hex Base32 및 Douglas Crockford Base32 통합 코덱**

- **아티팩트 좌표**: `com.msp:skill-base32-hex-crockford-codec:1.0.0`
- **개별 JAR 산출물**: `modules/skill-base32-hex-crockford-codec/build/libs/skill-base32-hex-crockford-codec-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 4648 표준 Base32 (`A-Z`, `2-7`)**:
   - 패딩(`=`) 포함/미포함 인코딩 및 디코딩 지원
   - 대소문자 무구분(Case-insensitive) 디코딩
2. **RFC 4648 Extended Hex Base32 (`0-9`, `A-V`)**:
   - DNSSEC 및 NSEC3 등 표준 정렬 보장이 필요한 규격 호환
3. **Douglas Crockford Base32 (`0-9`, `A-Z` 제외 `I`, `L`, `O`, `U`)**:
   - 사람의 시각적 오독 방지 및 URL 친화적 식별자(ULID 등) 생성에 적합
   - 디코딩 시 자동 에러 보정 (`i`/`I`/`l`/`L` → `1`, `o`/`O` → `0`, 하이픈 `-` 자동 무시)
4. **엄격한 데이터 정합성 검증**:
   - 비트 스트림 잔여 길이 검증 (길이 mod 8 불일치 시 예외 발생)
   - 허용되지 않은 문자 검출 시 즉각 `IllegalArgumentException` 발생

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.base32.Base32Codec;
import com.msp.base32.Base32Variant;
import java.nio.charset.StandardCharsets;

public class Base32Example {
    public static void main(String[] args) {
        byte[] data = "Hello World!".getBytes(StandardCharsets.UTF_8);

        // 1. RFC 4648 표준 Base32 (기본 패딩 포함)
        String rfc4648 = Base32Codec.encode(data);
        System.out.println("RFC 4648: " + rfc4648);
        // JBSWY3DPEBLW64TMMQ======

        // 2. RFC 4648 Extended Hex Base32
        String hex = Base32Codec.encode(data, Base32Variant.RFC4648_HEX);
        System.out.println("Base32Hex: " + hex);
        // 91IMOR3F41BMUSJCCG======

        // 3. Crockford Base32 (휴먼 에러 보정 지원)
        String crockford = Base32Codec.encode(data, Base32Variant.CROCKFORD);
        System.out.println("Crockford: " + crockford);
        // 91JPRV3F41BPYXKCCG

        // 4. Crockford 오독 보정 디코딩 (하이픈 포함 및 i/l -> 1, o -> 0 자동 치환)
        String userEntered = "91JPR-V3F41-BPYXK-CCG";
        byte[] decoded = Base32Codec.decode(userEntered, Base32Variant.CROCKFORD);
        System.out.println("Decoded: " + new String(decoded, StandardCharsets.UTF_8));
        // Hello World!
    }
}
```
