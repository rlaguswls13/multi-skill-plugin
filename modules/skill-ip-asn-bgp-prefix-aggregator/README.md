# 🌐 `skill-ip-asn-bgp-prefix-aggregator`

> **순수 Java 21 기반 IP 경로 요약(Route Summarization) & CIDR 프리픽스 집약(BGP Route Aggregator) 라이브러리**

- **아티팩트 좌표**: `com.msp:skill-ip-asn-bgp-prefix-aggregator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-asn-bgp-prefix-aggregator/build/libs/skill-ip-asn-bgp-prefix-aggregator-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **BGP 라우팅 테이블 최적화 (Route Summarization)**:
   - 인접한 2의 거듭제곱 형제 서브넷(Sibling Subnets)을 상위 수퍼넷으로 자동 병합 (예: `192.0.2.0/25` + `192.0.2.128/25` → `192.0.2.0/24`)
   - 4개 /24 서브넷을 단일 /22 프리픽스로 다단계 고정점(Fixed-point) 자동 축약
2. **중복 및 포함 서브넷 제거 (Containment Elimination)**:
   - 상위 대역(예: `10.0.0.0/8`)에 완전히 포함되는 하위 대역(예: `10.1.2.0/24`)을 즉시 식별하여 무손실 제거
3. **IPv4 & IPv6 통합 지원**:
   - `BigInteger` 비트 연산을 통해 IPv4 (32-bit) 및 IPv6 (128-bit) 경로를 완벽 지원
4. **집약 통계 및 분석**:
   - 원본 프리픽스 수, 집약 후 프리픽스 수, 경로 축소율(`reductionPercentage`) 실시간 계산

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.bgp.BgpPrefixAggregator;
import com.msp.bgp.AggregationResult;

public class BgpExample {
    public static void main(String[] args) {
        // 1. 방대한 IP 대역 목록 집약
        AggregationResult result = BgpPrefixAggregator.aggregate(
                "10.0.0.0/24",
                "10.0.1.0/24",
                "10.0.2.0/24",
                "10.0.3.0/24",
                "10.0.0.0/8",      // 상위 대역 -> 하위 4개 /24는 완전히 포함되어 잉여(Redundant) 제거
                "192.0.2.0/25",
                "192.0.2.128/25"   // 인접 형제 -> 192.0.2.0/24로 병합
        );

        System.out.println("원본 프리픽스 수: " + result.originalCount());   // 7
        System.out.println("집약 후 프리픽스 수: " + result.aggregatedCount()); // 2
        System.out.println("경로 축소율: " + String.format("%.1f%%", result.reductionPercentage())); // 71.4%

        // 2. 집약된 최소 CIDR 프리픽스 출력
        result.toCidrStrings().forEach(System.out::println);
        // 10.0.0.0/8
        // 192.0.2.0/24
    }
}
```
