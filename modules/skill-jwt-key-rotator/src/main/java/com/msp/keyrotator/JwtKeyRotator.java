package com.msp.keyrotator;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-safe JWT Key Rotator managing active issuance keys and grace-period verification keys.
 */
public final class JwtKeyRotator {

    public static final int DEFAULT_KEY_SIZE_BYTES = 32; // 256-bit for HMAC-SHA256
    public static final Duration DEFAULT_ROTATION_INTERVAL = Duration.ofDays(1);
    public static final Duration DEFAULT_GRACE_PERIOD = Duration.ofHours(12);

    private final int keyByteSize;
    private final Duration rotationInterval;
    private final Duration gracePeriod;
    private final Clock clock;
    private final boolean autoRotateOnAccess;
    private final Supplier<String> kidGenerator;
    private final SecureRandom secureRandom;

    private final ConcurrentHashMap<String, RotatedKey> keyMap = new ConcurrentHashMap<>();
    private final AtomicReference<RotatedKey> activeKey = new AtomicReference<>();
    private final ReentrantLock rotationLock = new ReentrantLock();

    public JwtKeyRotator() {
        this(new Builder());
    }

    public JwtKeyRotator(Duration rotationInterval, Duration gracePeriod) {
        this(builder().rotationInterval(rotationInterval).gracePeriod(gracePeriod));
    }

    public JwtKeyRotator(int keyByteSize, Duration rotationInterval, Duration gracePeriod, Clock clock) {
        this(builder()
                .keyByteSize(keyByteSize)
                .rotationInterval(rotationInterval)
                .gracePeriod(gracePeriod)
                .clock(clock));
    }

    private JwtKeyRotator(Builder builder) {
        this.keyByteSize = builder.keyByteSize;
        this.rotationInterval = builder.rotationInterval;
        this.gracePeriod = builder.gracePeriod;
        this.clock = builder.clock;
        this.autoRotateOnAccess = builder.autoRotateOnAccess;
        this.kidGenerator = builder.kidGenerator;
        this.secureRandom = builder.secureRandom;

        // Initialize first active key
        rotateKey();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Retrieves the current active key for signing new JWT tokens.
     * If autoRotateOnAccess is enabled and the key exceeded rotationInterval, rotates automatically.
     *
     * @return active {@link RotatedKey}
     */
    public RotatedKey getActiveKey() {
        RotatedKey current = activeKey.get();
        if (autoRotateOnAccess && current != null) {
            Instant now = clock.instant();
            if (now.isAfter(current.createdAt().plus(rotationInterval))) {
                rotationLock.lock();
                try {
                    // Double check active key after acquiring lock
                    current = activeKey.get();
                    if (now.isAfter(current.createdAt().plus(rotationInterval))) {
                        return rotateKey();
                    }
                } finally {
                    rotationLock.unlock();
                }
            }
        }
        return current;
    }

    /**
     * Retrieves a key by Key ID (kid) for token signature verification.
     *
     * @param kid the key ID
     * @return Optional containing the key if found and not expired, empty otherwise
     */
    public Optional<RotatedKey> getKey(String kid) {
        if (kid == null) {
            return Optional.empty();
        }
        RotatedKey key = keyMap.get(kid);
        if (key == null) {
            return Optional.empty();
        }
        if (key.isExpiredAt(clock.instant())) {
            // Lazy purge of expired key
            keyMap.remove(kid, key);
            return Optional.empty();
        }
        return Optional.of(key);
    }

    /**
     * Rotates to a newly generated key, marks the previous active key to expire after the grace period,
     * and returns the newly active key.
     *
     * @return newly active {@link RotatedKey}
     */
    public RotatedKey rotateKey() {
        rotationLock.lock();
        try {
            Instant now = clock.instant();
            RotatedKey previous = activeKey.get();

            if (previous != null) {
                // Transition previous key to grace period
                Instant graceExpiration = now.plus(gracePeriod);
                RotatedKey transitioned = new RotatedKey(
                        previous.kid(),
                        previous.secretKey(),
                        previous.createdAt(),
                        graceExpiration
                );
                keyMap.put(transitioned.kid(), transitioned);
            }

            // Create new active key
            String newKid = kidGenerator.get();
            byte[] secretBytes = new byte[keyByteSize];
            secureRandom.nextBytes(secretBytes);
            // While active, key validity extends to rotationInterval + gracePeriod
            Instant activeExpiresAt = now.plus(rotationInterval).plus(gracePeriod);

            RotatedKey newKey = new RotatedKey(newKid, secretBytes, now, activeExpiresAt);
            keyMap.put(newKid, newKey);
            activeKey.set(newKey);

            // Clean up any old keys that passed their grace period
            cleanExpiredKeys();

            return newKey;
        } finally {
            rotationLock.unlock();
        }
    }

    /**
     * Purges keys whose grace period has expired. Never purges the current active key.
     *
     * @return count of cleaned expired keys
     */
    public int cleanExpiredKeys() {
        Instant now = clock.instant();
        RotatedKey currentActive = activeKey.get();
        String activeKid = currentActive != null ? currentActive.kid() : null;

        int removedCount = 0;
        for (RotatedKey key : keyMap.values()) {
            if (!Objects.equals(key.kid(), activeKid) && key.isExpiredAt(now)) {
                if (keyMap.remove(key.kid(), key)) {
                    removedCount++;
                }
            }
        }
        return removedCount;
    }

    /**
     * Returns an unmodifiable list of all valid keys (active + unexpired grace period keys),
     * sorted with the newest key first.
     *
     * @return list of valid {@link RotatedKey}s
     */
    public List<RotatedKey> getAllValidKeys() {
        Instant now = clock.instant();
        return keyMap.values().stream()
                .filter(k -> !k.isExpiredAt(now))
                .sorted(Comparator.comparing(RotatedKey::createdAt).reversed())
                .toList();
    }

    /**
     * Total number of valid keys currently tracked.
     */
    public int getValidKeyCount() {
        return getAllValidKeys().size();
    }

    public Duration getRotationInterval() {
        return rotationInterval;
    }

    public Duration getGracePeriod() {
        return gracePeriod;
    }

    public static final class Builder {
        private int keyByteSize = DEFAULT_KEY_SIZE_BYTES;
        private Duration rotationInterval = DEFAULT_ROTATION_INTERVAL;
        private Duration gracePeriod = DEFAULT_GRACE_PERIOD;
        private Clock clock = Clock.systemUTC();
        private boolean autoRotateOnAccess = false;
        private Supplier<String> kidGenerator = () -> UUID.randomUUID().toString();
        private SecureRandom secureRandom = new SecureRandom();

        public Builder keyByteSize(int keyByteSize) {
            if (keyByteSize <= 0) {
                throw new IllegalArgumentException("Key byte size must be positive: " + keyByteSize);
            }
            this.keyByteSize = keyByteSize;
            return this;
        }

        public Builder rotationInterval(Duration rotationInterval) {
            this.rotationInterval = Objects.requireNonNull(rotationInterval, "rotationInterval cannot be null");
            return this;
        }

        public Builder gracePeriod(Duration gracePeriod) {
            this.gracePeriod = Objects.requireNonNull(gracePeriod, "gracePeriod cannot be null");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock cannot be null");
            return this;
        }

        public Builder autoRotateOnAccess(boolean autoRotate) {
            this.autoRotateOnAccess = autoRotate;
            return this;
        }

        public Builder kidGenerator(Supplier<String> kidGenerator) {
            this.kidGenerator = Objects.requireNonNull(kidGenerator, "kidGenerator cannot be null");
            return this;
        }

        public Builder secureRandom(SecureRandom secureRandom) {
            this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom cannot be null");
            return this;
        }

        public JwtKeyRotator build() {
            return new JwtKeyRotator(this);
        }
    }
}
