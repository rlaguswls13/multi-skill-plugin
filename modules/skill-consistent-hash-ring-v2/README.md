# Consistent Hash Ring v2 (`skill-consistent-hash-ring-v2`)

가상 노드(Virtual Nodes) 및 가중치(Weight) 지원 분산 샤딩용 Thread-Safe Consistent Hash Ring 모듈입니다.
분산 캐시, 데이터베이스 샤딩, 마이크로서비스 로드 밸런싱 환경에서 노드 증설 및 장애 제거 시 데이터 이동을 최소화합니다.

## 특징
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(Java Collections, SHA-256 MessageDigest)만 사용
- **가상 노드 (Virtual Nodes)**: 노드당 가상 노드를 배치하여 키 쏠림(Hotspot) 현상 방지 및 균등 분산 보장
- **가중치(Weight) 지원**: 고성능 서버에 더 높은 가중치를 부여하여 가상 노드 수를 비례적으로 할당
- **복제 노드 (Replica Nodes) 검색**: N개 복제 노드를 링 순회 방식으로 중복 없이 추출
- **Thread-Safe**: `ReentrantReadWriteLock` 기반 동시 읽기 최적화 및 안전한 쓰기 보장

## 사용법

```java
import com.msp.chashring.ConsistentHashRing;
import java.util.List;

// 1. Consistent Hash Ring 생성 (노드 가중치 1당 기본 가상 노드 100개)
ConsistentHashRing<String> ring = new ConsistentHashRing<>();

// 2. 물리 노드 등록 (기본 가중치 1 또는 사용자 정의 가중치)
ring.addNode("redis-node-1", 1); // 100 vnodes
ring.addNode("redis-node-2", 2); // 200 vnodes (2배 가중치)
ring.addNode("redis-node-3", 1); // 100 vnodes

// 3. 단일 라우팅 노드 검색
String primaryNode = ring.getNode("user-session:10492");
System.out.println("Primary node: " + primaryNode);

// 4. 장애 대응 및 분산 백업용 복제 노드 N개 검색
List<String> replicas = ring.getNodes("user-session:10492", 2);
System.out.println("Replica nodes: " + replicas);

// 5. 노드 제거 (장애 발생 또는 스케일인)
ring.removeNode("redis-node-1");
```
