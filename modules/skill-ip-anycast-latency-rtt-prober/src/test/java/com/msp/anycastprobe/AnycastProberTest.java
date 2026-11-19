package com.msp.anycastprobe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AnycastProberTest {

    @Test
    @DisplayName("Single endpoint probing measures latency metrics and jitter")
    void testSingleEndpointProbe() {
        MockSocketConnector mock = new MockSocketConnector()
                .setDefaultRule(Duration.ofMillis(30), Duration.ofMillis(2), 0.0);

        ProbeConfig config = new ProbeConfig(5, Duration.ofSeconds(1), Duration.ZERO, false);
        AnycastProber prober = new AnycastProber(mock, config);

        Endpoint ep = new Endpoint("tokyo-pop", "192.0.2.1", 443, "ap-northeast-1");
        ProbeStatistics stats = prober.probe(ep);

        assertEquals("tokyo-pop", stats.endpoint().name());
        assertEquals(5, stats.totalSamples());
        assertEquals(5, stats.successCount());
        assertEquals(0, stats.failureCount());
        assertEquals(0.0, stats.lossRate());

        // Latency between 28ms and 32ms
        assertTrue(stats.meanRtt().toMillis() >= 28 && stats.meanRtt().toMillis() <= 32);
        assertTrue(stats.minRtt().toMillis() <= stats.maxRtt().toMillis());
        assertNotNull(stats.jitter());
    }

    @Test
    @DisplayName("Parallel probing with Virtual Threads selects the optimal edge node")
    void testParallelProbingAndNodeSelection() {
        MockSocketConnector mock = new MockSocketConnector()
                .addRule("10.0.0.1", 443, Duration.ofMillis(80), Duration.ofMillis(5), 0.0)  // POP US
                .addRule("10.0.0.2", 443, Duration.ofMillis(15), Duration.ofMillis(2), 0.0)  // POP KR (Fastest)
                .addRule("10.0.0.3", 443, Duration.ofMillis(20), Duration.ofMillis(2), 0.5)  // POP JP (Unstable, 50% loss)
                .addRule("10.0.0.4", 443, Duration.ofMillis(220), Duration.ofMillis(10), 0.0); // POP EU

        ProbeConfig config = new ProbeConfig(6, Duration.ofSeconds(1), Duration.ZERO, true);
        AnycastProber prober = new AnycastProber(mock, config);

        List<Endpoint> endpoints = List.of(
                new Endpoint("us-east", "10.0.0.1", 443, "us-east-1"),
                new Endpoint("kr-central", "10.0.0.2", 443, "ap-northeast-2"),
                new Endpoint("jp-east", "10.0.0.3", 443, "ap-northeast-1"),
                new Endpoint("eu-west", "10.0.0.4", 443, "eu-west-1")
        );

        List<ProbeStatistics> results = prober.probeAll(endpoints);
        assertEquals(4, results.size());

        Optional<Endpoint> best = prober.selectBestEndpoint(endpoints);
        assertTrue(best.isPresent());
        assertEquals("kr-central", best.get().name());
    }

    @Test
    @DisplayName("Handling packet loss and total connection failures")
    void testPacketLossHandling() {
        MockSocketConnector mock = new MockSocketConnector()
                .setDefaultRule(Duration.ofMillis(100), Duration.ZERO, 1.0); // 100% loss

        ProbeConfig config = new ProbeConfig(3, Duration.ofMillis(500), Duration.ZERO, false);
        AnycastProber prober = new AnycastProber(mock, config);

        Endpoint deadEndpoint = new Endpoint("dead-pop", "198.51.100.1", 443);
        ProbeStatistics stats = prober.probe(deadEndpoint);

        assertEquals(3, stats.totalSamples());
        assertEquals(0, stats.successCount());
        assertEquals(3, stats.failureCount());
        assertEquals(1.0, stats.lossRate());
        assertEquals(Double.MAX_VALUE, stats.score());
    }

    @Test
    @DisplayName("Empty candidate list returns empty result")
    void testEmptyCandidates() {
        AnycastProber prober = new AnycastProber(new MockSocketConnector());
        assertTrue(prober.probeAll(List.of()).isEmpty());
        assertTrue(prober.selectBestEndpoint(List.of()).isEmpty());
    }
}
