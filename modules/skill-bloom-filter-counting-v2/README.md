# skill-bloom-filter-counting-v2

원소의 추가(Add)뿐만 아니라 삭제(Remove)를 지원하는 고성능 카운팅 블룸 필터(Counting Bloom Filter) 라이브러리입니다.

4-bit 니블(Nibble) 압축 카운터 및 8-bit 바이트 카운터를 지원하며, 카운터 오버플로로 인한 위음성(False Negative) 방지를 위해 포화(Saturation) 방어 로직과 Kirsch-Mitzenmacher 듀얼 해싱 기법을 적용했습니다.

---

## 🚀 주요 기능

- **4-bit / 8-bit 카운터 선택**:
  - `4-bit`: 1바이트당 2개의 카운터를 압축 저장하여 메모리 50% 절감 (최대 카운트 15 포화)
  - `8-bit`: 1바이트당 1개 카운터 저장 (최대 카운트 255 포화)
- **원소 삭제(Remove) 지원**: 표준 블룸 필터와 달리 `remove(item)`을 통해 해시 포지션 카운터를 감소시켜 오탐(False Positive) 누적 방지
- **포화 방어(Saturation Guard)**: 최대치(15 또는 255)에 도달한 카운터는 언더플로 및 위음성 방지를 위해 감소시키지 않음
- **Kirsch-Mitzenmacher 듀얼 해싱**: 고속 MurmurHash3 알고리즘 기반 2개 해시로 $k$개의 해시 위치를 $O(1)$로 유도
- **스레드 세이프(Thread-Safe)**: `ReentrantLock` 기반 동시성 안전 보장

---

## 📦 사용 예시

```java
import com.msp.countingbloom.CountingBloomFilter;

public class CountingBloomExample {
    public static void main(String[] args) {
        // 예상 원소 수 10,000개, 오탐율 1%, 4-bit 카운터로 생성
        CountingBloomFilter<String> filter = CountingBloomFilter.create(10000, 0.01, 4);

        // 1. 원소 추가
        filter.add("order_1001");
        filter.add("order_1002");

        // 2. 존재 여부 확인
        System.out.println("Contains 1001: " + filter.mightContain("order_1001")); // true
        System.out.println("Contains 9999: " + filter.mightContain("order_9999")); // false

        // 3. 원소 삭제
        boolean removed = filter.remove("order_1001");
        System.out.println("Removed 1001: " + removed); // true
        System.out.println("Contains 1001 after removal: " + filter.mightContain("order_1001")); // false

        // 4. 추정 원소 수 확인
        System.out.println("Approximate active elements: " + filter.approximateElementCount()); // 1
    }
}
```

---

## 🧪 테스트 검증 매트릭스

- `testCreateArgumentValidation`: 비정상 파라미터(0 이하 원소수, FPP 범위 외, 4/8 외 비트) 유효성 검증
- `test4BitAddAndRemove`: 4-bit 카운터 라이프사이클(추가, 존재 검사, 삭제, 비존재 삭제 실패) 검증
- `test8BitAddAndRemove`: 8-bit 카운터 라이프사이클 검증
- `test4BitSaturationBehavior`: 최대 카운트(15) 초과 삽입 시 포화 방어 및 위음성 방지 검증
- `testClear`: 필터 초기화 시 카운터 일괄 초기화 검증
- `testConcurrentOperations`: 다중 스레드 동시 추가/조회 무결성 검증
