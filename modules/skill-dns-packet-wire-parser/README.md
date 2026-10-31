# 📦 `skill-dns-packet-wire-parser`

> **순수 Java 21 표준 라이브러리 기반 RFC 1035 바이너리 와이어 포맷 DNS 패킷 파서 및 직렬화기**

- **아티팩트 좌표**: `com.msp:skill-dns-packet-wire-parser:1.0.0`
- **개별 JAR 산출물**: `modules/skill-dns-packet-wire-parser/build/libs/skill-dns-packet-wire-parser-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 1035 바이너리 와이어 규격 완벽 준수**:
   - 12바이트 DNS 헤더 파싱 및 직렬화 (ID, QR, Opcode, AA, TC, RD, RA, RCODE, QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT)
   - Question 섹션 및 Resource Record(Answer, Authority, Additional) 파싱/빌드
2. **DNS 포인터 압축 (0xC0..) 양방향 지원**:
   - 디코딩: `0xC0..` 포인터 오프셋 추적, 루프 순환 감지(Cycle Detection), 경계 초과 방지
   - 인코딩: 도메인 접미사 오프셋 테이블을 이용한 자동 포인터 압축 직렬화
3. **다양한 표준 레코드 타입 내장 지원**:
   - `A` (IPv4 4바이트)
   - `AAAA` (IPv6 16바이트)
   - `CNAME` / `NS` / `PTR` (도메인 네임)
   - `MX` (Preference 우선순위 + Mail Exchange 도메인)
   - `TXT` (길이 접두사 기반 RFC 1035 character-strings)
4. **견고한 예외 처리**:
   - 손상되거나 잘린 패킷에 대해 세부 원인을 담은 `DnsParseException` 발생

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.dnswire.*;
import java.util.List;

public class DnsWireExample {
    public static void main(String[] args) {
        // 1. DNS 쿼리 메시지 생성
        DnsMessage query = DnsMessage.createQuery(0x1234, "example.com", DnsRecordType.A);
        byte[] queryWire = query.toWire();

        // 2. 와이어 바이트 파싱
        DnsMessage parsedQuery = DnsMessage.parse(queryWire);
        System.out.println("Query ID: " + parsedQuery.header().id());
        System.out.println("Query Domain: " + parsedQuery.questions().getFirst().name());

        // 3. DNS 응답 메시지 생성
        DnsResourceRecord aRecord = DnsResourceRecord.createA("example.com", 300, "93.184.216.34");
        DnsResourceRecord mxRecord = DnsResourceRecord.createMx("example.com", 3600, 10, "mail.example.com");

        DnsMessage response = query.createResponse(DnsResponseCode.NOERROR.code(), List.of(aRecord, mxRecord));
        byte[] responseWire = response.toWire();

        // 4. 응답 파싱 및 레코드 추출
        DnsMessage parsedResponse = DnsMessage.parse(responseWire);
        for (DnsResourceRecord rr : parsedResponse.answers()) {
            if (rr.recordType() == DnsRecordType.A) {
                System.out.println("A: " + rr.asIpv4Address());
            } else if (rr.recordType() == DnsRecordType.MX) {
                System.out.println("MX: " + rr.asMx().exchange() + " (Priority: " + rr.asMx().priority() + ")");
            }
        }
    }
}
```
