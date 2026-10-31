# 📦 `skill-weighted-round-robin-balancer`

> **순수 Java 21 표준 라이브러리 기반 Nginx 및 LVS 표준 Smooth Weighted Round-Robin (SWRR) 가중치 로드밸런서**

- **아티팩트 좌표**: `com.msp:skill-weighted-round-robin-balancer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-weighted-round-robin-balancer/build/libs/skill-weighted-round-robin-balancer-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **Nginx 표준 Smooth Weighted Round-Robin (SWRR)**:
   - 단순 WRR의 치명적 결함인 특정 고가중치 서버로의 순간 트래픽 몰림(Bursting) 현상을 완전 해소
   - 요청마다 `currentWeight`를 동적으로 가산·감산하여 서버 간에 요청을 균일하고 부드럽게 분산
   - 예: 가중치 `{A:5, B:1, C:1}` 환경에서 `A, A, A, A, A, B, C` 대신 `A, A, B, A, C, A, A`로 균일 분산
2. **완벽한 스레드 안전성 (Thread-Safe)**:
   - `ReentrantLock` 기반 고속 동기화로 멀티스레드 및 가상 스레드(Virtual Threads) 환경에서 안전한 분산
3. **동적 서버 관리**:
   - 런타임 서버 추가, 삭제 및 가중치 동적 변경 지원
   - 일괄 선택(`selectSequence(int count)`) 및 상태 리셋(`reset()`) 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.wrr.SmoothWeightedRoundRobin;
import java.util.List;

public class WrrExample {
    public static void main(String[] args) {
        // 1. 로드밸런서 인스턴스 생성 및 서버 등록
        SmoothWeightedRoundRobin<String> wrr = new SmoothWeightedRoundRobin<>();
        wrr.addServer("192.168.1.10", 5); // 서버 A (가중치 5)
        wrr.addServer("192.168.1.11", 1); // 서버 B (가중치 1)
        wrr.addServer("192.168.1.12", 1); // 서버 C (가중치 1)

        // 2. 단일 서버 선택
        String selected = wrr.select();
        System.out.println("선택된 서버: " + selected);

        // 3. 7개 연속 선택 시 Nginx SWRR 분산 결과 확인
        wrr.reset();
        List<String> sequence = wrr.selectSequence(7);
        System.out.println("SWRR 균일 분산 시퀀스: " + sequence);
        // [192.168.1.10, 192.168.1.10, 192.168.1.11, 192.168.1.10, 192.168.1.12, 192.168.1.10, 192.168.1.10]
    }
}
```
