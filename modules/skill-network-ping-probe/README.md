# Network Ping & Port Probe (`skill-network-ping-probe`)

외부 3rd-party 의존성 없이 순수 Java 21 표준 네트워킹 API(`java.net.Socket`, `java.net.InetAddress`)만을 활용하여 원격/로컬 호스트의 TCP 포트 연결성 및 ICMP 가용성을 검사하고 RTT(왕복 시간)를 측정하는 경량 네트워크 프로브 유틸리티입니다.

## Features
- **TCP 핸드셰이크 프로브 (`pingTcp`)**: 타겟 호스트 및 포트에 소켓 핸드셰이크를 수행하여 접속 가능 여부 및 RTT(밀리초) 측정.
- **ICMP / OS 에코 프로브 (`pingIcmp`)**: `InetAddress.isReachable()`을 통해 네트워크 계층 도달 가능 여부 진단.
- **다중 포트 일괄 점검 (`probePorts`)**: 단일 호스트의 여러 서비스 포트(HTTP 80, HTTPS 443, DB 5432 등)를 일괄 스캔.
- **불변 결과 레코드 (`PingResult`)**: 호스트, 포트, 도달여부, RTT, 에러 메시지(실패 시)를 캡슐화.
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(`java.net.*`).

## Usage

```java
import com.msp.ping.NetworkPingProbe;
import com.msp.ping.PingResult;
import java.util.List;

// 1. 단일 TCP 서비스 포트 점검
PingResult dbResult = NetworkPingProbe.pingTcp("localhost", 5432, 2000);
if (dbResult.reachable()) {
    System.out.printf("Database is up! RTT: %d ms%n", dbResult.rttMillis());
} else {
    System.err.printf("Database unreachable: %s%n", dbResult.errorMessage());
}

// 2. 다중 포트 일괄 헬스체크
List<Integer> targetPorts = List.of(80, 443, 8080, 3306);
List<PingResult> portResults = NetworkPingProbe.probePorts("127.0.0.1", targetPorts, 1000);

for (PingResult res : portResults) {
    System.out.printf("Port %d: %s (%d ms)%n",
        res.port(),
        res.reachable() ? "OPEN" : "CLOSED (" + res.errorMessage() + ")",
        res.rttMillis()
    );
}

// 3. ICMP 도달 가능성 검사
PingResult hostCheck = NetworkPingProbe.pingIcmp("127.0.0.1", 1500);
System.out.println("Host reachable: " + hostCheck.reachable());
```
