# Universally Unique Lexicographically Sortable Identifier (ULID) (`skill-distributed-id-ulid`)

128비트 시간 순서 정렬 가능 무작위 고유 식별자(ULID, Universally Unique Lexicographically Sortable Identifier) 생성 및 인코딩/디코딩 라이브러리입니다.

UUIDv4의 비순차성으로 인한 데이터베이스 B-Tree 인덱스 단편화 문제를 해결하면서, 26자의 간결한 Crockford Base32 문자열로 URL 및 네트워크 전송 친화적인 식별자를 제공합니다.

## Features

- **시간 순서 정렬 보장 (Lexicographical Sortable)**:
  - 48비트 밀리초 타임스탬프(상위 비트) + 80비트 암호학적 난수 엔트로피(`SecureRandom`)
  - 문자열 사전순 비교(`compareTo`)와 128비트 부호 없는 정수 크기 비교가 완벽히 일치
- **Crockford Base32 표준 인코딩**:
  - `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (혼동 방지를 위해 I, L, O, U 제외)
  - 대소문자 무관 및 휴먼 에러 보정 파싱 지원 (`I`, `L` -> 1, `O` -> 0)
- **128비트 완전 호환성**:
  - `java.util.UUID` 상호 변환 (`toUuid()`, `fromUuid(UUID)`)
  - 16바이트 원시 배열 직렬화 (`toBytes()`, `fromBytes(byte[])`)
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리만으로 고성능 구현

## Usage

```java
import com.msp.ulid.Ulid;

import java.util.UUID;

// 1. 새 ULID 생성 (현재 시각 + SecureRandom)
Ulid ulid = Ulid.random();
System.out.println("ULID: " + ulid); // 예: 01HZY0XQ5R4Z1G8X2Z9W8V7B6A
System.out.println("Timestamp (ms): " + ulid.getTimestamp());

// 2. 문자열 및 바이트 변환
String str = ulid.toString(); // 26자 Crockford Base32
byte[] bytes = ulid.toBytes(); // 16바이트 바이너리

Ulid restored = Ulid.fromString(str);
assert ulid.equals(restored);

// 3. 표준 UUID 상호 변환
UUID standardUuid = ulid.toUuid();
Ulid fromUuid = Ulid.fromUuid(standardUuid);

// 4. 사전순 정렬
Ulid id1 = Ulid.fromTimestamp(1700000000000L);
Ulid id2 = Ulid.fromTimestamp(1700000001000L);
assert id1.compareTo(id2) < 0;
assert id1.toString().compareTo(id2.toString()) < 0;
```
