# TOTP Authenticator (`skill-totp-authenticator`)

RFC 6238 기반 Time-based One-Time Password (TOTP) 생성 및 검증 유틸리티입니다. 순수 Java 21 표준 라이브러리만을 사용하여 Google Authenticator, Microsoft Authenticator 등 표준 OTP 앱과의 완벽한 호환성을 제공합니다.

## Features
- **RFC 6238 & RFC 4226 준수**: 30초 시간 간격(Time-step), HMAC-SHA1 기반 6자리/8자리 OTP 코드 생성.
- **내장 Base32 코덱**: 외부 라이브러리 없이 RFC 4648 Base32 인코딩/디코딩 및 패딩 처리.
- **시간 허용 오차 윈도우 (Time Drift Tolerance)**: 클라이언트-서버 간 시계 불일치를 보정하기 위한 다중 스텝(`windowSteps`) 검증 지원.
- **보안 난수 키 생성**: `SecureRandom` 기반 비밀 키(Secret Key) 자동 생성.
- **QR 연동 URL 생성**: 표준 `otpauth://totp/...` URI 포맷 자동 빌더 지원.
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(`javax.crypto.Mac`, `java.security.MessageDigest`).

## Usage

```java
import com.msp.totp.TotpAuthenticator;
import java.time.Instant;

// 1. 신규 사용자를 위한 160-bit (20-byte) Base32 비밀 키 생성
String secretKey = TotpAuthenticator.generateSecretKey();
System.out.println("Secret Key: " + secretKey);

// 2. Google Authenticator 등록용 otpauth URI 생성 (QR 코드 생성기 입력용)
String otpAuthUrl = TotpAuthenticator.getOtpAuthUrl("MyCompany", "user@example.com", secretKey);
System.out.println("QR URI: " + otpAuthUrl);

// 3. 현재 시각 기준 6자리 OTP 코드 생성
String code = TotpAuthenticator.generateCurrentCode(secretKey);
System.out.println("Generated OTP: " + code);

// 4. 사용자 입력 코드 검증 (±1 스텝 허용: ±30초)
boolean isValid = TotpAuthenticator.verifyCode(secretKey, code);
System.out.println("Is Valid: " + isValid);
```
