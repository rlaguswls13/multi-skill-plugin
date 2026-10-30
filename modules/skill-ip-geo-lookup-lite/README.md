# skill-ip-geo-lookup-lite

외부 유료 DB 없이 순수 Java 21 비트마스크 연산 기반으로 IPv4 CIDR 대역 매칭, 사설망(RFC 1918), 루프백, 공인 IP 여부를 고속 판별하고 대역 매핑 테이블을 제공하는 단일 기술 모듈입니다.

## 주요 기능
- `Cidr.of("192.168.0.0/16").matches("192.168.1.10")`: 비트마스크 대역 매칭
- `IpClassifier.isPrivate(ip)`: 10.x, 172.16-31.x, 192.168.x 사설망 판별
- `IpClassifier.isLoopback(ip)`: 127.0.0.0/8, ::1, localhost 루프백 판별
- `IpClassifier.isPublic(ip)`: 공인 IP 여부 판별
- `CidrTable<T>`: CIDR 대역별 커스텀 메타데이터(국가코드, 망 분류) 고속 룩업
