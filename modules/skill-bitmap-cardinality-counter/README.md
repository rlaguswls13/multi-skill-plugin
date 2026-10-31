# Bitmap Cardinality Counter (`skill-bitmap-cardinality-counter`)

64비트 Long 워드 배열 기반의 고속 비트맵 및 원자적 카디널리티(Set-bit 수) 계산기 모듈입니다.
서비스의 DAU(일간 활성 사용자), WAU, MAU 집계, 리텐션(재방문율) 및 이탈률 분석에 최적화되어 있습니다.

## 특징
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(`long[]`, `Long.bitCount`, `Long.numberOfTrailingZeros`)만으로 구현
- **고속 집계 (O(N/64))**: `Long.bitCount` 하드웨어 명령어를 활용하여 수백만 건의 비트를 밀리초 단위로 집계
- **대규모 비트맵 자동 확장**: 사전 크기 할당 없이도 수천만~수억 단위의 bitIndex 설정 시 자동 동적 확장
- **비트 연산(집합 연산) 완벽 지원**:
  - `or()`: 기간 합집합 (DAU -> MAU 유니크 사용자 집계)
  - `and()`: 기간 교집합 (리텐션 / N일 연속 방문자 집계)
  - `andNot()`: 차집합 (특정 기간 이탈자 집계)
  - `xor()`: 대칭 차집합 (한 기간에만 활동한 사용자)
- **컴팩트 직렬화**: `toByteArray()` 및 `fromByteArray()`를 통해 Redis BITMAP 또는 RDB BLOB으로 무손실 고속 저장
- **Thread-Safe**: `ReentrantReadWriteLock` 기반 동시 읽기/쓰기 안전성 보장

## 사용법

```java
import com.msp.bitmapcounter.LongBitmapCounter;

// 1. 일자별 방문자 비트맵 생성 (사용자 ID를 비트 인덱스로 사용)
LongBitmapCounter day1 = new LongBitmapCounter();
day1.set(1001); // 사용자 1001번 방문
day1.set(1002);
day1.set(1003);

LongBitmapCounter day2 = new LongBitmapCounter();
day2.set(1002); // 1002번 재방문
day2.set(1004); // 신규 사용자 1004번

// 2. 일간 활성 사용자수 (DAU)
System.out.println("Day 1 DAU: " + day1.cardinality()); // 3
System.out.println("Day 2 DAU: " + day2.cardinality()); // 2

// 3. 2일간 순 사용자수 (UV / MAU 연산: OR 집합)
LongBitmapCounter union = day1.or(day2);
System.out.println("Total 2-Day UV: " + union.cardinality()); // 4 (1001, 1002, 1003, 1004)

// 4. 리텐션 (2일 연속 방문자: AND 집합)
LongBitmapCounter retained = day1.and(day2);
System.out.println("2-Day Retained Users: " + retained.cardinality()); // 1 (1002)

// 5. 이탈 사용자 (Day 1 방문 후 Day 2 미방문: AND-NOT 집합)
LongBitmapCounter churned = day1.andNot(day2);
System.out.println("Churned Users: " + churned.cardinality()); // 2 (1001, 1003)
```
