# 📦 `skill-ip-v6-normalizer-expander`

> **순수 Java 21 표준 라이브러리 기반 IPv6 주소 39자리 완전 전개, RFC 5952 표준 축약 및 IPv4 매핑 감지/추출기**

- **아티팩트 좌표**: `com.msp:skill-ip-v6-normalizer-expander:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-v6-normalizer-expander/build/libs/skill-ip-v6-normalizer-expander-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **39자리 완전 전개 (`expand`)**:
   - `::` 및 생략된 선행 0을 모두 복원하여 `0000:0000:0000:0000:0000:0000:0000:0001` 형태의 표준 8블록 39글자 소문자로 완전 확장
   - 데이터베이스 저장 및 정확한 문자열 인덱싱/대소 비교에 이상적
2. **RFC 5952 표준 정규 축약 (`compress`)**:
   - 각 16비트 블록의 선행 0 제거
   - 가장 긴 연속된 0 블록(2개 이상)을 `::`로 단 1회만 단축
   - 단일 0 블록은 `::`로 축약 금지 (RFC 5952 준수)
   - 동일 길이 0 블록이 복수 개 존재할 경우 첫 번째 블록 축약
   - 모든 16진수 문자 소문자화
3. **IPv4-Mapped IPv6 주소 감지 및 추출**:
   - `::ffff:x.x.x.x` 및 `0000:0000:0000:0000:0000:ffff:xxxx:xxxx` 감지 (`isIpv4Mapped`)
   - 임베디드 IPv4 주소를 10진수 포맷(`192.168.1.1`)으로 즉시 추출 (`extractIpv4`, `findIpv4`)
4. **엄격한 유효성 검증 (`isValid`)**:
   - 대괄호(`[...]`), 존 식별자(`%eth0`), IPv4 임베디드 표기법 유효성 정밀 검증

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.ipv6norm.Ipv6Normalizer;
import java.util.Optional;

public class Ipv6Example {
    public static void main(String[] args) {
        String shortIp = "2001:db8::1";

        // 1. 39자리 완전 전개 (39-character full expansion)
        String expanded = Ipv6Normalizer.expand(shortIp);
        System.out.println("Expanded: " + expanded);
        // 2001:0db8:0000:0000:0000:0000:0000:0001

        // 2. RFC 5952 표준 축약 (Canonical compression)
        String compressed = Ipv6Normalizer.compress(expanded);
        System.out.println("Compressed: " + compressed);
        // 2001:db8::1

        // 3. IPv4-mapped IPv6 주소 판별 및 IPv4 추출
        String mapped = "::ffff:192.168.1.100";
        if (Ipv6Normalizer.isIpv4Mapped(mapped)) {
            String ipv4 = Ipv6Normalizer.extractIpv4(mapped);
            System.out.println("Extracted IPv4: " + ipv4); // 192.168.1.100
        }

        // 4. 주소 유효성 검증
        boolean valid = Ipv6Normalizer.isValid("fe80::1%eth0");
        System.out.println("Is valid: " + valid); // true
    }
}
```
