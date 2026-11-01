# ⏳ `skill-bloom-filter-counting-sliding`

> **순수 Java 21 표준 라이브러리 기반 슬라이딩 윈도우 카운팅 블룸 필터 (Sliding-window Counting Bloom Filter)**

- **아티팩트 좌표**: `com.msp:skill-bloom-filter-counting-sliding:1.0.0`
- **개별 JAR 산출물**: `modules/skill-bloom-filter-counting-sliding/build/libs/skill-bloom-filter-counting-sliding-1.0.0.jar`
- **외부 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 특징 (Features)

1. **시간 기반 슬라이딩 윈도우 만료 (Time-bucketed Ring Buffer)**:
   - 지정된 윈도우 기간(`Duration windowDuration`)을 N개의 버킷(`bucketCount`)으로 분할
   - 시간이 경과함에 따라 오래된 시간대의 원소가 자동으로 만료되어 조회에서 제외
   - 윈도우를 벗어난 버킷의 메모리를 즉시 회수하는 `purgeExpiredBuckets()` 지원
2. **Murmur3 128-bit 듀얼 해싱 (Kirsch-Mitzenmacher 최적화)**:
   - $h_i(x) = (h_1(x) + i \cdot h_2(x)) \pmod m$ 방식을 사용하여 단 한 번의 128비트 해싱으로 $k$개의 최적 해시 함수 생성
3. **스레드 세이프 (Thread-Safe)**:
   - `ReentrantReadWriteLock` 기반 동시성 제어로 멀티스레드 환경에서 안전한 동시 읽기 및 쓰기 보장
4. **결합도 제로 & 경량 설계**:
   - Guava 등 무거운 서드파티 라이브러리 의존 없이 순수 Java 21 표준 클래스로만 동작

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.slidingbloom.SlidingCountingBloomFilter;
import java.time.Duration;

public class SlidingBloomExample {
    public static void main(String[] args) throws InterruptedException {
        // 1분 슬라이딩 윈도우, 6개 버킷 (버킷당 10초), 허용 오차율 1%, 예상 10,000건
        SlidingCountingBloomFilter<String> filter = new SlidingCountingBloomFilter<>(
                10_000, 
                0.01, 
                Duration.ofMinutes(1), 
                6
        );

        // 원소 추가
        filter.add("session-token-9871");
        filter.add("user-ip-203.0.113.1");

        // 소속 여부 확인
        System.out.println("Contains token: " + filter.mightContain("session-token-9871")); // true
        System.out.println("Contains absent: " + filter.mightContain("session-token-0000")); // false

        // 만료된 버킷 정리
        int purged = filter.purgeExpiredBuckets();
        System.out.println("Purged buckets: " + purged);

        // 전체 초기화
        filter.clear();
    }
}
```
