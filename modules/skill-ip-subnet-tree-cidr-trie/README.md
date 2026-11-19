# 📦 `skill-ip-subnet-tree-cidr-trie`

> **IPv4 및 IPv6 CIDR 서브넷 검색, 최장 일치(LPM) 및 중복/포함 판별을 위한 이진 래딕스 트라이(Binary Radix Trie)**

- **아티팩트 좌표**: `com.msp:skill-ip-subnet-tree-cidr-trie:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-subnet-tree-cidr-trie/build/libs/skill-ip-subnet-tree-cidr-trie-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **IPv4 & IPv6 완전 지원 (Dual-Stack Radix Trie)**:
   - IPv4 32비트 트리 및 IPv6 128비트 트리를 각각 독립 루트로 관리하여 네임스페이스 충돌 없이 고속 비트 트리 탐색
   - DNS 질의 없이 문자열 IP를 바이트 배열로 고속 파싱하는 경량 `IpParser` 내장
2. **최장 일치 검색 (Longest Prefix Match - LPM)**:
   - 라우팅 테이블 및 ACL(Access Control List) 방화벽 룰 평가 시 최우선 순위로 가장 세밀한 서브넷 탐색 (`findLongestMatchingSubnet`)
3. **서브넷 중복 및 포함 관계 분석 (`findOverlappingSubnets`)**:
   - 특정 CIDR 블록과 겹치는(상위 조상 포함 서브넷, 동일 서브넷, 하위 서브넷) 모든 블록을 한 번의 트리 탐색으로 수집
4. **계층적 일치 서브넷 탐색 (`findAllMatchingSubnets`)**:
   - 루트부터 리프까지 특정 IP를 포함하는 모든 서브넷을 짧은 접두사에서 긴 접두사 순서로 반환
5. **동시성 및 스레드 안전성**:
   - `ReentrantReadWriteLock` 기반 동시 읽기(LPM 검색) 극대화 및 안전한 동적 서브넷 추가/삭제/가지치기(Pruning)

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.subnettrie.SubnetTrie;
import java.util.List;
import java.util.Optional;

public class SubnetTrieExample {
    public static void main(String[] args) {
        SubnetTrie<String> trie = new SubnetTrie<>();

        // 1. 서브넷 및 정책 등록
        trie.insert("10.0.0.0/8", "Internal-Corp-Net");
        trie.insert("10.1.0.0/16", "DataCenter-Seoul");
        trie.insert("10.1.5.0/24", "Database-Cluster");
        trie.insert("2001:db8:abcd::/48", "IPv6-DC");

        // 2. 최장 일치(LPM) 라우팅 검색
        Optional<String> policy = trie.findLongestMatchingSubnet("10.1.5.23");
        policy.ifPresent(p -> System.out.println("Routed to: " + p)); // Database-Cluster

        // 3. 서브넷 중복 감지 (Overlap Detection)
        List<String> overlaps = trie.findOverlappingSubnets("10.1.0.0/20");
        System.out.println("Overlapping subnets: " + overlaps);

        // 4. IP 포함 여부 확인
        boolean allowed = trie.containsIp("10.1.5.100");
        System.out.println("Contains IP: " + allowed); // true
    }
}
```
