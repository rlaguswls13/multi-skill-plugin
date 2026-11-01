# 📦 `skill-ip-trie-radix-router`

> **IPv4 및 IPv6 CIDR 블록 대상 초고속 Longest Prefix Match (LPM) 이진 비트 라딕스 트라이 라우터**

- **아티팩트 좌표**: `com.msp:skill-ip-trie-radix-router:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-trie-radix-router/build/libs/skill-ip-trie-radix-router-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **IPv4 & IPv6 이진 비트와이즈 트라이 (Binary Bitwise Radix Trie)**:
   - 외부 의존성 및 DNS 조회 없이 순수 비트 시프트 연산으로 IPv4(32비트)와 IPv6(128비트) 분기 처리.
   - 최장 접두사 일치(Longest Prefix Match, LPM) 알고리즘을 통한 가장 구체적인 라우트 자동 선택.
2. **CIDR 및 디폴트 라우트 지원**:
   - `0.0.0.0/0`, `::/0` 디폴트 게이트웨이 루트 노드 처리.
   - 겹치는 서브넷(`10.0.0.0/8`, `10.1.0.0/16`, `10.1.2.0/24`) 간 완벽한 우선순위 매칭.
3. **스레드 세이프 (Thread-Safe)**:
   - `ReentrantReadWriteLock` 기반 동시 읽기 극대화 및 안전한 락 제어.
   - 라우트 추가, 삭제(`removeRoute`), 조회(`lookup`), 전체 초기화(`clear`) 지원.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.iproute.IpRadixRouter;
import java.util.Optional;

public class IpRouterExample {
    public static void main(String[] args) {
        IpRadixRouter<String> router = new IpRadixRouter<>();

        // 1. CIDR 라우트 등록 (우선순위 계층)
        router.addRoute("0.0.0.0/0", "default-internet-gateway");
        router.addRoute("10.0.0.0/8", "corporate-vpc");
        router.addRoute("10.1.0.0/16", "internal-services");
        router.addRoute("10.1.2.0/24", "auth-cluster");

        // 2. IPv6 라우트 등록
        router.addRoute("2001:db8::/32", "ipv6-external-hub");

        // 3. 최장 접두사 일치 (LPM) 조회
        Optional<String> authRoute = router.lookup("10.1.2.55");
        System.out.println("Match for 10.1.2.55: " + authRoute.orElse("None")); // auth-cluster

        Optional<String> vpcRoute = router.lookup("10.20.1.1");
        System.out.println("Match for 10.20.1.1: " + vpcRoute.orElse("None")); // corporate-vpc

        Optional<String> defaultRoute = router.lookup("8.8.8.8");
        System.out.println("Match for 8.8.8.8: " + defaultRoute.orElse("None")); // default-internet-gateway
    }
}
```
