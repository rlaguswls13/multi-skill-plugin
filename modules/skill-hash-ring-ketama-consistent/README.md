# skill-hash-ring-ketama-consistent

Memcached 및 Nginx 표준 규격의 Ketama MD5 일관된 해시 링(Consistent Hash Ring) 라이브러리입니다.
노드당 160개의 가상 포인트(Virtual Points)를 배치하여 키 분산의 균등성과 캐시 노드 증설/제거 시 재배치(Remapping) 최소화를 보장합니다.

## 주요 기능
- **표준 Ketama MD5 해시 알고리즘**: 16바이트 MD5 다이제스트로부터 4개의 32-bit 리틀엔디안 정수 가상 포인트를 도출
- **가중치(Weight) 지원**: 단위 가중치당 160개(40 chunks × 4 points)의 가상 포인트를 생성하여 고성능 노드에 비례 분산
- **O(log N) 조회 속도**: `TreeMap` 기반의 원형 링(Ring) 및 `ceilingKey` 이진 탐색
- **최소 재배치 보장**: 노드 추가/제거 시 오직 $1/N$의 키만 재배치되어 캐시 스탬피드 및 부하 방지
- **Thread-Safe**: `ReentrantReadWriteLock` 기반 동시 조회 최적화

## 사용 예제

```java
import com.msp.ketama.KetamaHashRing;

public class Example {
    public static void main(String[] args) {
        KetamaHashRing<String> ring = new KetamaHashRing<>();

        // 가중치에 따른 노드 등록 (160 vs 320 virtual points)
        ring.addNode("memcached-node-1", 1);
        ring.addNode("memcached-node-2", 2);

        // 일관된 해시 탐색
        String targetNode = ring.getNode("user-session:12345");
        System.out.println("Mapped to: " + targetNode);

        // 노드 제거
        ring.removeNode("memcached-node-1");
    }
}
```
