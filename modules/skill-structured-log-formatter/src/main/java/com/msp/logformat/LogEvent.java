package com.msp.logformat;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable log event payload for structured logging.
 */
public record LogEvent(
        Instant timestamp,
        String level,
        String logger,
        String thread,
        String message,
        Map<String, ?> context,
        Throwable error
) {
    public LogEvent {
        timestamp = timestamp != null ? timestamp : Instant.now();
        level = level != null ? level.toUpperCase() : "INFO";
        logger = logger != null ? logger : "";
        thread = thread != null ? thread : Thread.currentThread().getName();
        message = message != null ? message : "";
        context = context != null ? Collections.unmodifiableMap(new LinkedHashMap<>(context)) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Instant timestamp;
        private String level = "INFO";
        private String logger;
        private String thread;
        private String message;
        private Map<String, Object> context;
        private Throwable error;

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder level(LogLevel level) {
            this.level = level != null ? level.name() : "INFO";
            return this;
        }

        public Builder level(String level) {
            this.level = level != null ? level : "INFO";
            return this;
        }

        public Builder logger(String logger) {
            this.logger = logger;
            return this;
        }

        public Builder logger(Class<?> clazz) {
            this.logger = clazz != null ? clazz.getName() : "";
            return this;
        }

        public Builder thread(String thread) {
            this.thread = thread;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder message(String format, Object... args) {
            this.message = (format != null && args != null && args.length > 0)
                    ? String.format(format, args)
                    : format;
            return this;
        }

        public Builder context(Map<String, ?> context) {
            if (context != null) {
                if (this.context == null) {
                    this.context = new LinkedHashMap<>();
                }
                this.context.putAll(context);
            }
            return this;
        }

        public Builder context(String key, Object value) {
            if (this.context == null) {
                this.context = new LinkedHashMap<>();
            }
            this.context.put(key, value);
            return this;
        }

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public LogEvent build() {
            return new LogEvent(timestamp, level, logger, thread, message, context, error);
        }
    }
}
