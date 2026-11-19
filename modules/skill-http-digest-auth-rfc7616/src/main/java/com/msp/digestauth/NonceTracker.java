package com.msp.digestauth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe server-side nonce registry and monotonic {@code nc} (nonce-count) replay detector.
 */
public final class NonceTracker {

    private static final HexFormat HEX = HexFormat.of();

    private final Clock clock;
    private final SecureRandom random;
    private final ConcurrentHashMap<String, NonceState> nonces = new ConcurrentHashMap<>();

    private record NonceState(Instant expiresAt, long lastNc) {
    }

    public NonceTracker() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    public NonceTracker(Clock clock) {
        this(clock, new SecureRandom());
    }

    public NonceTracker(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Issues a cryptographically random 128-bit hex nonce valid for {@code ttl}.
     */
    public String issueNonce(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String nonce = HEX.formatHex(bytes);
        nonces.put(nonce, new NonceState(clock.instant().plus(ttl), 0L));
        return nonce;
    }

    /**
     * Registers an externally supplied nonce with a TTL (useful for deterministic tests).
     */
    public void registerNonce(String nonce, Duration ttl) {
        Objects.requireNonNull(nonce, "nonce must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (nonce.isBlank()) {
            throw new IllegalArgumentException("nonce must not be blank");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        nonces.put(nonce, new NonceState(clock.instant().plus(ttl), 0L));
    }

    /**
     * Atomically validates that {@code nonce} exists, is not expired, and {@code ncHex} is a valid
     * 8-digit hex value strictly greater than the previously accepted {@code nc} for this nonce.
     */
    public ReplayCheckResult validateAndAdvance(String nonce, String ncHex) {
        if (nonce == null || nonce.isBlank()) {
            return ReplayCheckResult.UNKNOWN_NONCE;
        }
        long parsedNc = parseNcHex(ncHex);
        if (parsedNc <= 0) {
            return ReplayCheckResult.INVALID_NC;
        }

        Instant now = clock.instant();
        AtomicReference<ReplayCheckResult> outcome = new AtomicReference<>(ReplayCheckResult.UNKNOWN_NONCE);

        nonces.compute(nonce, (key, state) -> {
            if (state == null) {
                outcome.set(ReplayCheckResult.UNKNOWN_NONCE);
                return null;
            }
            if (!now.isBefore(state.expiresAt())) {
                outcome.set(ReplayCheckResult.EXPIRED_NONCE);
                return null; // evict expired nonce
            }
            if (parsedNc <= state.lastNc()) {
                outcome.set(ReplayCheckResult.REPLAYED_NC);
                return state;
            }
            outcome.set(ReplayCheckResult.VALID);
            return new NonceState(state.expiresAt(), parsedNc);
        });

        return outcome.get();
    }

    /**
     * Removes all expired nonces from memory and returns the number of entries purged.
     */
    public int purgeExpired() {
        Instant now = clock.instant();
        int[] removed = {0};
        nonces.forEach((k, v) -> {
            if (!now.isBefore(v.expiresAt()) && nonces.remove(k, v)) {
                removed[0]++;
            }
        });
        return removed[0];
    }

    public int activeCount() {
        return nonces.size();
    }

    private static long parseNcHex(String ncHex) {
        if (ncHex == null || ncHex.length() != 8) {
            return -1L;
        }
        try {
            return Long.parseUnsignedLong(ncHex, 16);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
