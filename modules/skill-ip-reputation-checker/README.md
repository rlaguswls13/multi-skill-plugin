# 🛡️ skill-ip-reputation-checker

IPv4 주소 및 CIDR 블록에 대한 초고속 비트마스크 필터링 및 화이트리스트 우선 평판 검사 라이브러리입니다.

## ✨ 주요 기능
- **초고속 32비트 비트마스크 연산**: 정규화된 32비트 Unsigned Long 비트마스킹으로 O(1) 수준의 서브넷 대역 포함 여부 판별
- **CIDR 대역 및 단일 IP 지원**: `0.0.0.0/0`부터 `/32`까지의 모든 서브넷 마스크 및 단일 IP 등록
- **화이트리스트 우선 정책**: 블랙리스트 대역 안에 포함된 IP라도 명시적 화이트리스트 룰에 의해 안전하게 예외 허용
- **스레드 세이프(Thread-Safe)**: 락-프리 기반 동시성 지원(`CopyOnWriteArrayList`)으로 멀티스레드 인프라/게이트웨이에 최적화
- **Pure Java 21**: 외부 런타임 3rd-party 의존성 Zero

---

## 🚀 빠른 시작 (Quick Start)

### 1. 블랙리스트 및 화이트리스트 등록과 검사
```java
import com.msp.ipreputation.IpReputationChecker;
import com.msp.ipreputation.ReputationCheckResult;

public class Example {
    public static void main(String[] args) {
        IpReputationChecker checker = new IpReputationChecker()
                .blockCidr("192.168.1.0/24", "Blocked Internal Subnet")
                .allowIp("192.168.1.50", "Whitelisted Admin Station")
                .blockIp("203.0.113.195", "Known Malicious Scanner");

        // 1. 차단 대역 내 일반 IP -> 차단
        System.out.println(checker.isBlocked("192.168.1.10")); // true

        // 2. 차단 대역 내 화이트리스트 IP -> 허용
        System.out.println(checker.isBlocked("192.168.1.50")); // false

        // 3. 상세 진단 결과 조회
        ReputationCheckResult result = checker.check("203.0.113.195");
        System.out.println("Blocked: " + result.blocked());
        System.out.println("Rule: " + result.matchedRule());
        System.out.println("Reason: " + result.reason());
    }
}
```
