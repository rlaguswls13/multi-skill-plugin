package com.msp.procwatchdog;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable execution policy for {@link ProcessWatchdog}.
 */
public final class WatchdogPolicy {

    private final Duration maxWallTime;
    private final Duration maxIdleTime;
    private final long maxOutputBytes;
    private final Duration destroyGracePeriod;
    private final Duration pollInterval;
    private final Path workingDirectory;
    private final Map<String, String> environment;

    private WatchdogPolicy(Builder b) {
        if (b.maxWallTime != null && (b.maxWallTime.isZero() || b.maxWallTime.isNegative())) {
            throw new IllegalArgumentException("maxWallTime must be positive");
        }
        if (b.maxIdleTime != null && (b.maxIdleTime.isZero() || b.maxIdleTime.isNegative())) {
            throw new IllegalArgumentException("maxIdleTime must be positive");
        }
        if (b.destroyGracePeriod == null || b.destroyGracePeriod.isNegative()) {
            throw new IllegalArgumentException("destroyGracePeriod must be non-negative");
        }
        if (b.pollInterval == null || b.pollInterval.isZero() || b.pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        this.maxWallTime = b.maxWallTime;
        this.maxIdleTime = b.maxIdleTime;
        this.maxOutputBytes = b.maxOutputBytes;
        this.destroyGracePeriod = b.destroyGracePeriod;
        this.pollInterval = b.pollInterval;
        this.workingDirectory = b.workingDirectory;
        this.environment = Collections.unmodifiableMap(new LinkedHashMap<>(b.environment));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration maxWallTime() {
        return maxWallTime;
    }

    public Duration maxIdleTime() {
        return maxIdleTime;
    }

    /** Combined stdout + stderr byte quota; {@code <= 0} means unlimited. */
    public long maxOutputBytes() {
        return maxOutputBytes;
    }

    public Duration destroyGracePeriod() {
        return destroyGracePeriod;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public Path workingDirectory() {
        return workingDirectory;
    }

    public Map<String, String> environment() {
        return environment;
    }

    public static final class Builder {
        private Duration maxWallTime = Duration.ofSeconds(30);
        private Duration maxIdleTime = null;
        private long maxOutputBytes = 10L * 1024L * 1024L; // 10 MiB default
        private Duration destroyGracePeriod = Duration.ofMillis(300);
        private Duration pollInterval = Duration.ofMillis(15);
        private Path workingDirectory = null;
        private final Map<String, String> environment = new LinkedHashMap<>();

        public Builder maxWallTime(Duration maxWallTime) {
            this.maxWallTime = maxWallTime;
            return this;
        }

        public Builder maxIdleTime(Duration maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
            return this;
        }

        public Builder maxOutputBytes(long maxOutputBytes) {
            this.maxOutputBytes = maxOutputBytes;
            return this;
        }

        public Builder destroyGracePeriod(Duration destroyGracePeriod) {
            this.destroyGracePeriod = Objects.requireNonNull(destroyGracePeriod);
            return this;
        }

        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = Objects.requireNonNull(pollInterval);
            return this;
        }

        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder putEnv(String key, String value) {
            Objects.requireNonNull(key, "env key must not be null");
            Objects.requireNonNull(value, "env value must not be null");
            this.environment.put(key, value);
            return this;
        }

        public WatchdogPolicy build() {
            return new WatchdogPolicy(this);
        }
    }
}
