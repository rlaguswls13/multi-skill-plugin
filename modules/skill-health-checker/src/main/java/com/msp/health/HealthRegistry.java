package com.msp.health;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for managing named health checks and their execution timeouts.
 */
public class HealthRegistry {

    public record Registration(String name, HealthCheck check, Duration timeout) {
        public Registration {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(check, "check must not be null");
            timeout = timeout != null ? timeout : Duration.ofSeconds(5);
        }
    }

    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();
    private final Duration defaultTimeout;

    public HealthRegistry() {
        this(Duration.ofSeconds(5));
    }

    public HealthRegistry(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : Duration.ofSeconds(5);
    }

    public HealthRegistry register(String name, HealthCheck check) {
        return register(name, check, defaultTimeout);
    }

    public HealthRegistry register(String name, HealthCheck check, Duration timeout) {
        registrations.put(name, new Registration(name, check, timeout != null ? timeout : defaultTimeout));
        return this;
    }

    public boolean unregister(String name) {
        return registrations.remove(name) != null;
    }

    public Optional<Registration> get(String name) {
        return Optional.ofNullable(registrations.get(name));
    }

    public Set<String> getNames() {
        return Collections.unmodifiableSet(registrations.keySet());
    }

    public Map<String, Registration> getAll() {
        return Collections.unmodifiableMap(registrations);
    }

    public int size() {
        return registrations.size();
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    public void clear() {
        registrations.clear();
    }
}
