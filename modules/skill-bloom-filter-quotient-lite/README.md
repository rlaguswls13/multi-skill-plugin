# 🧪 `skill-bloom-filter-quotient-lite`

> **순수 Java 21 기반 고성능 몫 필터 (Quotient Filter) 라이브러리: 캐시 친화적 선형 탐사(Linear Probing) 및 원소 삭제(True Deletion) 지원**

- **아티팩트 좌표**: `com.msp:skill-bloom-filter-quotient-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-bloom-filter-quotient-lite/build/libs/skill-bloom-filter-quotient-lite-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **몫 필터(Quotient Filter) 핵심 아키텍처**:
   - $p$-bit 해시를 $q$-bit 몫(Quotient, $2^q$ 버킷 인덱스)과 $r$-bit 나머지(Remainder, 지문)로 분할
   - 단일 원시 `long[]` 배열 내에 3개의 상태 플래그(`isOccupied`, `isContinuation`, `isShifted`)와 나머지를 컴팩트하게 비트 패킹
2. **진정한 원소 삭제(True Element Deletion)**:
   - 일반 블룸 필터(Bloom Filter)와 달리 원소 삭제(`remove`)를 완벽 지원
   - 삭제 시 우측 클러스터 원소들을 좌측으로 시프트(Shift-left)하여 런(Run) 및 불변성 유지
3. **뛰어난 CPU 캐시 지역성(Cache-Friendly Linear Probing)**:
   - 다중 독립 해시 함수 접근 대신 선형 탐사(Linear Probing) 클러스터링을 적용하여 캐시 미스(L1/L2 Cache Miss)를 최소화
4. **동적 팩토리 및 목표 오탐률(FPP) 지원**:
   - `QuotientFilter.withCapacity(expectedCapacity, fpp)`로 $q$와 $r$ 자동 최적 계산
   - 제네릭 타입(`QuotientFilter<T>`) 및 커스텀 해시 함수 매핑 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.quotient.QuotientFilter;

public class QuotientFilterExample {
    public static void main(String[] args) {
        // 1. 예상 1,000개 원소 및 목표 오탐률 1% 필터 생성
        QuotientFilter<String> qf = QuotientFilter.withCapacity(1000, 0.01);

        // 2. 원소 추가 (put)
        qf.put("order_id_1001");
        qf.put("order_id_1002");
        qf.put("order_id_1003");

        System.out.println("Filter count: " + qf.getCount()); // 3
        System.out.println("Load factor: " + qf.getLoadFactor());

        // 3. 존재 여부 확인 (contains)
        System.out.println("Contains 1001? " + qf.contains("order_id_1001")); // true
        System.out.println("Contains 9999? " + qf.contains("order_id_9999")); // false

        // 4. 원소 삭제 (remove) - 몫 필터 고유 기능!
        boolean removed = qf.remove("order_id_1002");
        System.out.println("Removed 1002? " + removed); // true
        System.out.println("Contains 1002 now? " + qf.contains("order_id_1002")); // false
        System.out.println("Contains 1001 still? " + qf.contains("order_id_1001")); // true
    }
}
```
