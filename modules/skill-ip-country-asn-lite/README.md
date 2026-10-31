# 📦 `skill-ip-country-asn-lite`

> **순수 Java 21 기반 인메모리 IPv4 Range & CIDR 고속 국가 코드(ISO-3166 alpha-2) 및 ASN 룩업 프로브**

- **아티팩트 좌표**: `com.msp:skill-ip-country-asn-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-country-asn-lite/build/libs/skill-ip-country-asn-lite-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **IPv4 IP Range 및 CIDR 표기법 등록**:
   - `registerRange(startIp, endIp, countryCode, asn, asName)`
   - `registerCidr("1.1.1.0/24", countryCode, asn, asName)`
2. **이진 탐색(Binary Search) 기반 $O(\log N)$ 초고속 조회**:
   - 32비트 Unsigned Long 변환을 통한 메모리 절약 및 나노초 단위 탐색
   - 락(Lock) 없는 불변 배열 참조를 통해 동시성 조회 시 경합(Contention) 0 달성
3. **최장 접두사 일치 (Longest Prefix Match)**:
   - 상위 대역(예: `/8`)과 하위 세부 서브넷(예: `/16`, `/32`)이 중첩 등록되어도 가장 세부적인 규칙을 정확히 매칭

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.ipasn.IpAsnLookupEngine;
import com.msp.ipasn.IpGeoAsnRecord;
import java.util.Optional;

public class IpAsnExample {
    public static void main(String[] args) {
        IpAsnLookupEngine engine = new IpAsnLookupEngine();

        // 1. CIDR 및 IP 범위 등록
        engine.registerCidr("1.1.1.0/24", "US", 13335, "Cloudflare, Inc.");
        engine.registerRange("211.234.0.0", "211.234.255.255", "KR", 4766, "Korea Telecom");

        // 2. IP 룩업
        Optional<IpGeoAsnRecord> result = engine.lookup("1.1.1.1");
        result.ifPresent(record -> {
            System.out.println("국가: " + record.countryCode()); // US
            System.out.println("ASN: " + record.asn());         // 13335
            System.out.println("AS기관: " + record.asName());     // Cloudflare, Inc.
        });

        // 3. 국내 IP 룩업
        Optional<IpGeoAsnRecord> krResult = engine.lookup("211.234.10.5");
        krResult.ifPresent(record -> {
            System.out.println("국가: " + record.countryCode()); // KR
            System.out.println("ISP: " + record.asName());      // Korea Telecom
        });
    }
}
```
