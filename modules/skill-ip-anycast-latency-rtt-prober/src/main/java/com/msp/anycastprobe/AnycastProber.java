package com.msp.anycastprobe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Multi-region IP Anycast latency prober and edge node selector.
 * Leverages Java 21 Virtual Threads to probe multiple network POPs concurrently.
 */
public class AnycastProber {

    private final SocketConnector connector;
    private final ProbeConfig config;

    public AnycastProber(SocketConnector connector, ProbeConfig config) {
        this.connector = Objects.requireNonNull(connector, "connector must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public AnycastProber(SocketConnector connector) {
        this(connector, ProbeConfig.defaultConfig());
    }

    public AnycastProber() {
        this(new DefaultSocketConnector(), ProbeConfig.defaultConfig());
    }

    public SocketConnector getConnector() {
        return connector;
    }

    public ProbeConfig getConfig() {
        return config;
    }

    /**
     * Probes a single endpoint sequentially according to configured sample count.
     */
    public ProbeStatistics probe(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint must not be null");

        // Warmup attempt (discarded)
        if (config.warmup()) {
            try {
                connector.measureConnectRtt(endpoint.host(), endpoint.port(), config.timeout());
            } catch (Exception ignored) {
                // Warmup failures do not count
            }
        }

        List<ProbeSample> samples = new ArrayList<>(config.samplesPerEndpoint());
        for (int i = 0; i < config.samplesPerEndpoint(); i++) {
            try {
                Duration rtt = connector.measureConnectRtt(endpoint.host(), endpoint.port(), config.timeout());
                samples.add(ProbeSample.success(rtt));
            } catch (Exception e) {
                samples.add(ProbeSample.failure(e.getMessage()));
            }

            if (!config.interval().isZero() && i < config.samplesPerEndpoint() - 1) {
                try {
                    Thread.sleep(config.interval().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return ProbeStatistics.compute(endpoint, samples);
    }

    /**
     * Probes multiple endpoints in parallel using Java 21 Virtual Threads.
     */
    public List<ProbeStatistics> probeAll(List<Endpoint> endpoints) {
        Objects.requireNonNull(endpoints, "endpoints must not be null");
        if (endpoints.isEmpty()) {
            return Collections.emptyList();
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ProbeStatistics>> futures = new ArrayList<>(endpoints.size());
            for (Endpoint ep : endpoints) {
                futures.add(executor.submit(() -> probe(ep)));
            }

            List<ProbeStatistics> results = new ArrayList<>(endpoints.size());
            for (Future<ProbeStatistics> f : futures) {
                try {
                    results.add(f.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Probing interrupted", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("Probing task failed", e.getCause());
                }
            }
            return results;
        }
    }

    /**
     * Selects the optimal endpoint with the lowest composite score (balanced latency, jitter, loss).
     */
    public Optional<ProbeStatistics> selectOptimal(List<ProbeStatistics> statsList) {
        if (statsList == null || statsList.isEmpty()) {
            return Optional.empty();
        }
        return statsList.stream().min(ProbeStatistics::compareTo);
    }

    /**
     * Concurrently probes all candidates and directly returns the best endpoint.
     */
    public Optional<Endpoint> selectBestEndpoint(List<Endpoint> endpoints) {
        List<ProbeStatistics> stats = probeAll(endpoints);
        return selectOptimal(stats).map(ProbeStatistics::endpoint);
    }
}
