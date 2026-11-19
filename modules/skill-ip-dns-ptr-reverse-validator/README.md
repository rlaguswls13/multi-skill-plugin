# 📦 `skill-ip-dns-ptr-reverse-validator`

> **순수 Java 21 기반 정방향 확인 역방향 DNS(Forward-Confirmed Reverse DNS, FCrDNS) 유효성 검증기 (메일 서버 안티스푸핑, MTA, SSH 접속 필터링)**

- **아티팩트 좌표**: `com.msp:skill-ip-dns-ptr-reverse-validator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-dns-ptr-reverse-validator/build/libs/skill-ip-dns-ptr-reverse-validator-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 표준 3단계 FCrDNS 검증 알고리즘 (`FcrdnsValidator`)**:
   - **Step 1 (역방향 조회)**: 클라이언트 IP의 PTR 레코드를 조회하여 연결된 호스트 도메인 이름 확인 (`198.51.100.1` -> `mail.example.com`).
   - **Step 2 (정방향 조회)**: Step 1에서 도출된 도메인의 A/AAAA 레코드를 다시 조회 (`mail.example.com` -> `[198.51.100.1, ...]`).
   - **Step 3 (일치 확인)**: 정방향 조회 결과 IP 목록에 원래 접속을 시도한 클라이언트 IP가 존재하는지 대조하여 최종 `CONFIRMED` 판정.
2. **안티스푸핑(Anti-Spoofing) 탐지 및 세부 상태 코드 (`FcrdnsStatus`)**:
   - `CONFIRMED`: FCrDNS 정방향 확인 성공.
   - `NO_PTR_RECORD`: PTR 레코드 미등록 IP.
   - `IP_MISMATCH`: 공격자가 소유한 IP의 PTR 레코드를 타사 도메인(예: `bank.com`)으로 조작하였으나 정방향 IP와 불일치하는 스푸핑 시도 탐지.
   - `FORWARD_LOOKUP_FAILED`: 역방향 레코드는 있으나 해당 도메인의 A/AAAA 레코드가 존재하지 않거나 소실됨.
3. **오프라인 테스트용 인메모리 DNS 리졸버 (`InMemoryDnsResolver`)**:
   - 단위 테스트나 로컬 개발 환경에서 실제 외부 DNS 네트워크 요청 없이 가상 PTR/A/AAAA 레코드를 주입하여 100% 격리된 테스트 가능.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.dnsreverse.*;
import java.net.InetAddress;

public class FcrdnsExample {
    public static void main(String[] args) throws Exception {
        // 1. 운영 환경: 시스템 기본 DNS 리졸버 사용
        FcrdnsValidator validator = FcrdnsValidator.system();

        // 2. 수신 클라이언트 IP 유효성 검증
        String clientIp = "198.51.100.25";
        FcrdnsValidationResult result = validator.validate(clientIp);

        if (result.isConfirmed()) {
            System.out.println("FCrDNS Confirmed! Verified Domains: " + result.confirmedDomains());
        } else {
            System.err.printf("FCrDNS Rejected! Status=%s, Details=%s%n",
                    result.status(), result.details());
        }
    }
}
```
