# 📦 `skill-bloom-filter-scalable`

> **순수 Java 21 표준 라이브러리 기반 계층 동적 증설형 Scalable Bloom Filter (Almeida et al. 2007)**

- **아티팩트 좌표**: `com.msp:skill-bloom-filter-scalable:1.0.0`
- **개별 JAR 산출물**: `modules/skill-bloom-filter-scalable/build/libs/skill-bloom-filter-scalable-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **용량 초과 시 계층 동적 증설 (Dynamic Layer Scaling)**:
   - 고정 크기 블룸 필터와 달리 초기 용량을 초과해도 재해싱 없이 신규 필터 계층을 동적으로 추가
2. **복합 위양성률 상한 보장 (Tightened False Positive Rate)**:
   - 기하급수적 계층 증설 시 각 계층의 목표 오류율을 $P_i = P_0 \cdot r^i$로 축소(Tightening)하여 전체 복합 오류율이 초기 지정한 $P$ 이하로 유지되도록 수학적 수렴 보장
3. **MurmurHash3 128-bit 고속 비트 해싱**:
   - Kirsch-Mitzenmacher 듀얼 해싱 기법(`h1 + i * h2`)을 적용하여 $k$개의 최적 해시 함수를 고속으로 계산
4. **완전한 스레드 안전성 (Thread-Safe)**:
   - `ReentrantReadWriteLock` 기반 동시 읽기 다중화 및 계층 증설 시 원자적 쓰기 락 제어

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.scalablebloom.ScalableBloomFilter;

public class ScalableBloomExample {
    public static void main(String[] args) {
        // 초기 용량 1,000건, 목표 위양성률 1% (0.01) 설정
        ScalableBloomFilter<String> bloomFilter = new ScalableBloomFilter<>(1_000, 0.01);

        // 1. 데이터 삽입 (초기 용량을 초과하여 대량 추가 시 자동 계층 증설)
        for (int i = 0; i < 5_000; i++) {
            bloomFilter.add("user-id-" + i);
        }

        // 2. 포함 여부 확인 (위음성 False Negative = 0% 보장)
        System.out.println("Contains user-id-100? " + bloomFilter.contains("user-id-100")); // true
        System.out.println("Contains user-id-99999? " + bloomFilter.contains("user-id-99999")); // false (99% 확률)

        // 3. 계층 및 용량 통계 확인
        System.out.println("Active layers: " + bloomFilter.layerCount());          // e.g. 3 layers
        System.out.println("Total capacity: " + bloomFilter.totalCapacity());       // e.g. 7,000
        System.out.println("Total added: " + bloomFilter.approxElementCount());     // 5,000
    }
}
```
