# 📦 `skill-bloom-filter-partitioned`

> **CPU 캐시 지역성(Cache-Locality) 최적화 및 해시 충돌 편향을 제거한 분할 블룸 필터 (Partitioned Bloom Filter)**

- **아티팩트 좌표**: `com.msp:skill-bloom-filter-partitioned:1.0.0`
- **개별 JAR 산출물**: `modules/skill-bloom-filter-partitioned/build/libs/skill-bloom-filter-partitioned-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **분할 블룸 필터 (Partitioned Architecture)**:
   - 전체 비트 배열($M$)을 $k$개의 독립된 슬라이스($s = M/k$ 비트)로 분할.
   - 각 해시 함수는 오직 자신에게 배정된 독립 슬라이스 내의 비트 1개만 매핑하여 해시 충돌 편향을 제거하고 CPU 캐시 히트율을 극대화.
2. **Kirsch-Mitzenmacher Murmur3-128 듀얼 해싱**:
   - 단 1회의 128비트 Murmur3 해시 연산으로 두 개의 64비트 해시($h_1, h_2$)를 생성.
   - $h(i) = (h_1 + i \cdot h_2) \pmod s$ 수식을 통해 $k$개의 해시 함수를 가볍고 빠르게 유도.
3. **수학적 최적화 및 동시성 보장**:
   - 목표 허용 오차($fpp$) 및 예상 삽입량 기반 자동 최적 슬라이스 크기 및 해시 개수 산출.
   - `ReentrantReadWriteLock` 기반 스레드 세이프 비트 연산.
   - 실시간 이론적 FPP 추정치(`currentFppEstimate()`) 및 전체 리셋(`clear()`) 제공.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.partbloom.PartitionedBloomFilter;

public class PartitionedBloomExample {
    public static void main(String[] args) {
        // 1. 예상 삽입량 10,000건, 목표 오차율 1% (0.01)로 생성
        PartitionedBloomFilter<String> filter = PartitionedBloomFilter.create(10_000, 0.01);

        // 2. 요소 추가
        filter.add("https://api.example.com/items/101");
        filter.add("https://api.example.com/items/102");

        // 3. 포함 여부 확인 (Zero False Negatives 보장)
        System.out.println("Contains 101: " + filter.mightContain("https://api.example.com/items/101")); // true
        System.out.println("Contains 999: " + filter.mightContain("https://api.example.com/items/999")); // false

        // 4. 슬라이스 구조 및 추정 오차율 확인
        System.out.println("Slice bits: " + filter.sliceBits());
        System.out.println("Hash count: " + filter.hashCount());
        System.out.println("Estimated FPP: " + filter.currentFppEstimate());
    }
}
```
