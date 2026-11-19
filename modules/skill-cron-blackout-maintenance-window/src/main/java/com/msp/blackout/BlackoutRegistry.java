package com.msp.blackout;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe registry of maintenance blackout windows with conflict detection,
 * active window lookup, and cascading post-window delayed execution calculation.
 */
public final class BlackoutRegistry {

    private static final int MAX_ADVANCE_STEPS = 1024;

    private final ConcurrentHashMap<String, BlackoutWindow> windows = new ConcurrentHashMap<>();

    /**
     * Registers or replaces a blackout window by its {@link BlackoutWindow#id()}.
     */
    public void register(BlackoutWindow window) {
        Objects.requireNonNull(window, "window must not be null");
        windows.put(window.id(), window);
    }

    /**
     * Unregisters a blackout window by {@code id}. Returns {@code true} if it existed.
     */
    public boolean unregister(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return windows.remove(id) != null;
    }

    public Optional<BlackoutWindow> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(windows.get(id));
    }

    /**
     * Returns all registered blackout windows sorted deterministically by {@code id}.
     */
    public List<BlackoutWindow> listWindows() {
        return windows.values().stream()
                .sorted(Comparator.comparing(BlackoutWindow::id))
                .toList();
    }

    /**
     * Returns {@code true} if {@code instant} falls inside any registered blackout window.
     */
    public boolean isInBlackout(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        for (BlackoutWindow window : windows.values()) {
            if (window.contains(instant)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all blackout windows currently active at {@code instant}, sorted deterministically by {@code id}.
     */
    public List<BlackoutWindow> activeWindows(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return windows.values().stream()
                .filter(w -> w.contains(instant))
                .sorted(Comparator.comparing(BlackoutWindow::id))
                .toList();
    }

    /**
     * Computes the earliest {@link Instant} at or after {@code requested} that does not fall
     * inside any registered blackout window. Handles overlapping and back-to-back windows.
     */
    public Instant nextAllowedTime(Instant requested) {
        Objects.requireNonNull(requested, "requested must not be null");
        Instant candidate = requested;

        for (int step = 0; step < MAX_ADVANCE_STEPS; step++) {
            Instant maxEnd = null;
            for (BlackoutWindow window : windows.values()) {
                Optional<Instant> end = window.activeWindowEnd(candidate);
                if (end.isPresent()) {
                    Instant e = end.get();
                    if (maxEnd == null || e.isAfter(maxEnd)) {
                        maxEnd = e;
                    }
                }
            }
            if (maxEnd == null) {
                return candidate;
            }
            if (!maxEnd.isAfter(candidate)) {
                throw new BlackoutConflictException("Blackout window did not advance past " + candidate);
            }
            candidate = maxEnd;
        }

        throw new BlackoutConflictException(
                "No allowed execution time found after " + MAX_ADVANCE_STEPS
                        + " blackout transitions starting from " + requested);
    }

    /**
     * Resolves a requested execution time against the registry using the given {@link BlackoutPolicy}.
     */
    public Optional<Instant> schedule(Instant requested, BlackoutPolicy policy) {
        Objects.requireNonNull(requested, "requested must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        List<BlackoutWindow> active = activeWindows(requested);
        if (active.isEmpty()) {
            return Optional.of(requested);
        }

        return switch (policy) {
            case DELAY -> Optional.of(nextAllowedTime(requested));
            case SKIP -> Optional.empty();
            case REJECT -> {
                String ids = active.stream().map(BlackoutWindow::id).collect(Collectors.joining(", "));
                throw new BlackoutConflictException(
                        "Requested time " + requested + " conflicts with blackout window(s): " + ids);
            }
        };
    }
}
