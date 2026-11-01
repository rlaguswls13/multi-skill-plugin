package com.msp.prockill;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration options for process tree termination.
 */
public record ProcessKillConfig(
    Duration gracePeriod,
    Duration forceKillTimeout,
    boolean safeMode,
    boolean dryRun,
    boolean allowKillCurrentProcess
) {
    public ProcessKillConfig {
        Objects.requireNonNull(gracePeriod, "gracePeriod cannot be null");
        Objects.requireNonNull(forceKillTimeout, "forceKillTimeout cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ProcessKillConfig defaultConfig() {
        return builder().build();
    }

    public static final class Builder {
        private Duration gracePeriod = Duration.ofSeconds(5);
        private Duration forceKillTimeout = Duration.ofSeconds(2);
        private boolean safeMode = true;
        private boolean dryRun = false;
        private boolean allowKillCurrentProcess = false;

        public Builder gracePeriod(Duration gracePeriod) {
            this.gracePeriod = Objects.requireNonNull(gracePeriod, "gracePeriod cannot be null");
            return this;
        }

        public Builder forceKillTimeout(Duration forceKillTimeout) {
            this.forceKillTimeout = Objects.requireNonNull(forceKillTimeout, "forceKillTimeout cannot be null");
            return this;
        }

        public Builder safeMode(boolean safeMode) {
            this.safeMode = safeMode;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder allowKillCurrentProcess(boolean allow) {
            this.allowKillCurrentProcess = allow;
            return this;
        }

        public ProcessKillConfig build() {
            return new ProcessKillConfig(gracePeriod, forceKillTimeout, safeMode, dryRun, allowKillCurrentProcess);
        }
    }
}
