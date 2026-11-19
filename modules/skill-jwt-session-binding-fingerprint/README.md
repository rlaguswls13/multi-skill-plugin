# 📦 `skill-jwt-session-binding-fingerprint`

> **순수 Java 21 기반 클라이언트 TLS 채널 & 브라우저 핑거프린트 암호학적 JWT 세션 바인딩 (토큰 탈취 및 리플레이 공격 방어)**

- **아티팩트 좌표**: `com.msp:skill-jwt-session-binding-fingerprint:1.0.0`
- **개별 JAR 산출물**: `modules/skill-jwt-session-binding-fingerprint/build/libs/skill-jwt-session-binding-fingerprint-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **클라이언트 컨텍스트 기반 암호학적 바인딩 해시 (`TokenBindingManager`)**:
   - 클라이언트 IP 서브넷(IPv4 `/24`, IPv6 `/64` 마스킹을 통한 모바일 로밍 허용 모드 또는 완전 일치 `EXACT` 모드 지원).
   - 정규화된 User-Agent 해시.
   - mTLS 인증서 지문(`tlsCertThumbprint`), TLS 채널 바인딩 데이터(`tlsChannelBinding`), 디바이스 고유 키(`deviceKeyId`).
2. **JWT `bnd` 클레임 생성 및 검증**:
   - `createBindingClaims()`로 JWT 페이로드에 삽입할 표준 클레임 맵(`bnd`, `bnd_ip`, `bnd_mode`, `bnd_tls`, `bnd_dev`) 생성.
   - HMAC-SHA256 또는 SHA-256을 통한 클라이언트 위변조 방지.
3. **상세 불일치 진단 및 심각도 분류 (`TokenBindingValidationResult`)**:
   - 토큰 검증 실패 시 불일치 원인(`IP_SUBNET_MISMATCH`, `USER_AGENT_MISMATCH`, `TLS_CHANNEL_MISMATCH`, `DEVICE_KEY_MISMATCH` 등)과 보안 심각도(`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`)를 세분화하여 SIEM/SOC 관제 모니터링에 연계 가능.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.sessbind.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SessionBindingExample {
    public static void main(String[] args) {
        byte[] serverSecretKey = "server-secret-key-32-bytes-long!".getBytes(StandardCharsets.UTF_8);

        // 1. 로그인 시 클라이언트 연결 컨텍스트 캡처
        ClientFingerprintContext initialContext = ClientFingerprintContext.builder()
                .ipAddress("198.51.100.24")
                .ipBindingMode(IpBindingMode.SUBNET) // /24 서브넷 허용
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .tlsCertThumbprint("sha256-cert-thumbprint-hex")
                .deviceKeyId("device-hardware-uuid-42")
                .build();

        // 2. JWT 발행 시 바인딩 클레임 주입
        Map<String, Object> bindingClaims = TokenBindingManager.createBindingClaims(initialContext, serverSecretKey);
        System.out.println("Injected Claims: " + bindingClaims);

        // 3. API 요청 수신 시 현재 요청 컨텍스트 검증
        ClientFingerprintContext incomingContext = ClientFingerprintContext.builder()
                .ipAddress("198.51.100.89") // 동일 /24 대역 로밍
                .ipBindingMode(IpBindingMode.SUBNET)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .tlsCertThumbprint("sha256-cert-thumbprint-hex")
                .deviceKeyId("device-hardware-uuid-42")
                .build();

        TokenBindingValidationResult result = TokenBindingManager.validate(incomingContext, bindingClaims, serverSecretKey);

        if (result.isValid()) {
            System.out.println("Token binding verified successfully!");
        } else {
            System.err.printf("ALERT: Token binding mismatch! Type=%s, Severity=%s, Details=%s%n",
                    result.mismatchType(), result.severity(), result.details());
        }
    }
}
```
