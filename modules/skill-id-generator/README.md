# 🆔 skill-id-generator

고성능 분산 고유 ID 생성기 모음집 (Java 21 순수 표준 라이브러리, 의존성 Zero).

Twitter Snowflake(64비트 정수), TSID(Crockford Base32 13자리 시간순 정렬 ID), NanoID(21자리 암호화 난수 URL-Safe ID), RFC 9562 UUIDv7을 통합 제공합니다.

---

## 🚀 주요 기능

1. **SnowflakeIdGenerator**:
   - 41비트 밀리초 타임스탬프 (약 69.7년) + 10비트 노드 ID (0~1023) + 12비트 밀리초당 시퀀스 (0~4095).
   - 64비트 양수 `long` 생성, 밀리초당 최대 4,096,000개 ID 발급.
   - 시스템 시계 역전 방어(Clock Backwards Defense) 내장.
   - ID로부터 원본 생성 시각(`Instant`), 노드 ID, 시퀀스 역추출 지원.

2. **TsidGenerator**:
   - 64비트 Time-Sorted ID.
   - Crockford Base32 인코딩(알파벳 `0-9, A-Z` 중 I, L, O, U 제외) 기반 **13자리** 문자열 생성.
   - 알파벳 대소문자 무관 및 유사 문자(O↔0, I/L↔1) 내성 디코딩 지원.

3. **NanoIdGenerator**:
   - `SecureRandom` 기반 암호학적으로 안전한 URL-Safe 난수 ID.
   - 비트마스크(Bitmask) 알고리즘으로 모듈로 편향(Modulo Bias) 0%.
   - 커스텀 알파벳 및 길이 지원 (기본 21자리).

4. **IdGenerators (Unified Facade)**:
   - `snowflake()`, `tsid()`, `nanoId()`, `uuidV4()`, `uuidV7()`를 원스톱으로 호출 가능한 통합 파사드.

---

## 💻 사용 예제

### 1. Snowflake ID 생성

```java
import com.msp.idgen.SnowflakeIdGenerator;
import com.msp.idgen.IdGenerators;
import java.time.Instant;

// 파사드 사용 (기본 노드 1)
long id = IdGenerators.snowflake().nextId();

// 특정 노드(0~1023) 인스턴스 생성
SnowflakeIdGenerator generator = new SnowflakeIdGenerator(42L);
long uniqueId = generator.nextId();

// ID로부터 정보 추출
Instant createdAt = generator.extractInstant(uniqueId);
long nodeId = generator.extractNodeId(uniqueId);
long seq = generator.extractSequence(uniqueId);
```

### 2. TSID (Time-Sorted ID) 생성

```java
import com.msp.idgen.TsidGenerator;
import com.msp.idgen.IdGenerators;

// 13자리 Crockford Base32 문자열 생성 (예: "01JAX1BCDE90F")
String tsidStr = IdGenerators.tsid().nextTsid();

// 원시 64비트 long 추출 및 문자열 변환
long tsidLong = IdGenerators.tsid().nextLong();
String encoded = TsidGenerator.toBase32(tsidLong);
long decoded = TsidGenerator.fromBase32(encoded);
```

### 3. NanoID 생성

```java
import com.msp.idgen.NanoIdGenerator;
import com.msp.idgen.IdGenerators;

// 기본 21자리 NanoID (예: "V1StGXR8_Z5jdHi6B-myT")
String id21 = IdGenerators.nanoId();

// 10자리 커스텀 길이
String id10 = IdGenerators.nanoId(10);
```

### 4. UUIDv7 (RFC 9562 시간순 정렬 UUID)

```java
import com.msp.idgen.IdGenerators;
import java.util.UUID;

// 48비트 타임스탬프가 접두사로 포함된 UUIDv7
UUID v7 = IdGenerators.uuidV7();
```

---

## 🧪 빌드 및 테스트

```bash
.\gradlew.bat :modules:skill-id-generator:test
```
