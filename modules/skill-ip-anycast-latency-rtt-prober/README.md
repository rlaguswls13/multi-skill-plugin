# Multi-Region IP Anycast Latency & RTT Socket Prober (`skill-ip-anycast-latency-rtt-prober`)

Pure Java 21 latency measurement and edge routing optimizer. Probes TCP handshake round-trip time (RTT) across multiple Anycast POPs / IP endpoints in parallel using Java 21 Virtual Threads and identifies the optimal edge node.

## Features

- **Microsecond TCP Handshake RTT**: Opens non-blocking TCP socket handshakes to measure exact connect round-trip time.
- **Java 21 Virtual Threads**: Probes dozens or hundreds of Anycast POPs simultaneously without blocking thread pool resources.
- **Comprehensive Latency Metrics**: Calculates Min, Max, Mean, Median, Jitter (standard deviation), and failure / packet loss rate.
- **Composite Edge Node Selector**: Selects the best POP using a weighted scoring model balancing raw latency, jitter stability, and packet loss.
- **Mockable Architecture**: Includes `SocketConnector` interface and `MockSocketConnector` for fast, deterministic unit testing without live network connections.
- **Pure Java 21**: Zero 3rd party runtime dependencies.

## Runnable Code Example

```java
import com.msp.anycastprobe.AnycastProber;
import com.msp.anycastprobe.Endpoint;
import com.msp.anycastprobe.ProbeConfig;
import com.msp.anycastprobe.ProbeStatistics;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class AnycastExample {
    public static void main(String[] args) {
        // 1. Configure prober (5 samples per POP, 2s timeout, 10ms sample interval, discard warmup)
        ProbeConfig config = new ProbeConfig(5, Duration.ofSeconds(2), Duration.ofMillis(10), true);
        AnycastProber prober = new AnycastProber();

        // 2. Define Anycast candidate POP endpoints
        List<Endpoint> pops = List.of(
                new Endpoint("seoul-edge-01", "1.1.1.1", 443, "ap-northeast-2"),
                new Endpoint("tokyo-edge-01", "8.8.8.8", 443, "ap-northeast-1"),
                new Endpoint("singapore-edge-01", "9.9.9.9", 443, "ap-southeast-1")
        );

        // 3. Concurrently probe all endpoints via Virtual Threads
        List<ProbeStatistics> results = prober.probeAll(pops);
        for (ProbeStatistics stat : results) {
            System.out.printf("[%s] Mean RTT: %d ms, Jitter: %d ms, Loss: %.1f%%%n",
                    stat.endpoint().name(),
                    stat.meanRtt().toMillis(),
                    stat.jitter().toMillis(),
                    stat.lossRate() * 100);
        }

        // 4. Automatically select the best edge node
        Optional<Endpoint> bestPop = prober.selectBestEndpoint(pops);
        bestPop.ifPresent(ep -> System.out.println("Selected Best Edge POP: " + ep.name()));
    }
}
```
