# 64-Bit Bitmask Permission Evaluator (`skill-bitmask-permission`)

단일 `long` (64비트 정수) 값 하나로 최대 64개의 권한 및 역할을 초고속 비트 연산으로 관리하는 인가(Authorization) 연산 모듈입니다.

## Features
- **64 Distinct Permissions**: 하나의 Long 컬럼으로 64개 권한을 압축 저장하여 DB 스키마 단순화 및 쿼리 극대화.
- **Microsecond Bitwise Logic**: AND(`&`), OR(`|`), NOT(`~`), XOR(`^`) 비트 연산 기반 나노초 단위 검증.
- **Fluent & Static APIs**: 유연한 객체 지향 체이닝 메서드 및 고성능 정적 유틸리티 함수 모두 지원.
- **Predefined Flags**: `READ`, `WRITE`, `UPDATE`, `DELETE`, `EXECUTE`, `ADMIN`, `EXPORT`, `AUDIT` 기본 내장.

## Usage

```java
// 1. 권한 부여 및 생성
BitmaskPermission perm = BitmaskPermission.of(PermissionFlag.READ, PermissionFlag.WRITE);

// 2. 권한 검사
if (perm.has(PermissionFlag.WRITE)) {
    // 글 작성 허용
}

// 3. 플래그 추가 및 취소
perm.grant(PermissionFlag.ADMIN);
perm.revoke(PermissionFlag.WRITE);

// 4. DB 저장을 위한 long 마스크 추출
long dbValue = perm.getMask();
```
