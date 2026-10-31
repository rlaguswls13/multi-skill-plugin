# JWT Claims Extractor (`skill-jwt-claims-extractor`)

외부 3rd-party 의존성(Jackson, Gson, JJWT 등) 전혀 없이 순수 Java 21 표준 라이브러리 및 내장 경량 JSON 파서로 JWT 헤더와 페이로드를 초고속 파싱하는 모듈입니다.
서명 검증 이전의 사전 감사 로깅, API 게이트웨이 라우팅, 클레임/역할 추출에 최적화되어 있습니다.

## 특징
- **Zero 3rd-party Dependencies**: Jackson, Gson, Nimbus, JJWT 등의 외부 라이브러리 없이 순수 Java 21 표준 라이브러리 구동
- **내장 경량 JSON 파서**: `SimpleJsonParser` 재귀 하강 파서를 내장하여 외부 파서 설정이나 의존성 충돌 제로
- **RFC 7519 표준 클레임 매핑**: `sub`, `iss`, `aud`, `exp`, `iat`, `nbf`, `jti` 표준 필드 지원 및 `Instant` 변환
- **유연한 역할 파싱**: JSON 배열(`["ADMIN"]`) 및 구분자 분리 문자열(`"ADMIN,USER"` 또는 `"read write"`) 모두 자동 변환
- **토큰 만료 판별**: `isExpired()`를 통한 간편한 유효성 사전 검사

## 사용법

```java
import com.msp.jwtextract.JwtClaimsExtractor;
import com.msp.jwtextract.JwtPayload;
import com.msp.jwtextract.JwtHeader;

String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." ;

// 1. 헤더 추출
JwtHeader header = JwtClaimsExtractor.parseHeader(token);
System.out.println("Algorithm: " + header.alg()); // "HS256"

// 2. 페이로드 전체 파싱
JwtPayload payload = JwtClaimsExtractor.parse(token);
System.out.println("Subject: " + payload.sub());
System.out.println("Expired: " + payload.isExpired());

// 3. 특정 클레임 및 역할 목록 즉시 조회
List<String> roles = JwtClaimsExtractor.getRoleList(token, "roles");
String tenantId = (String) JwtClaimsExtractor.getClaim(token, "tenantId");
```
