package com.msp.jwk;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RFC 7517 JSON Web Key Set (JWKS) dynamic parser and key cache resolver.
 * Parses JWKS JSON payloads, caches decoded keys, and resolves PublicKeys by Key ID (kid).
 */
public final class JwkSetResolver {

    private final Duration cacheTtl;
    private volatile Instant loadedAt;
    private final Map<String, JwkKey> keyMap = new ConcurrentHashMap<>();
    private final List<JwkKey> keys = new CopyOnWriteArrayList<>();

    public JwkSetResolver(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public JwkSetResolver() {
        this(Duration.ofHours(1));
    }

    /**
     * Parses and loads JWKS JSON format containing a "keys" array:
     * <pre>
     * {
     *   "keys": [
     *     { "kty": "RSA", "kid": "k1", "n": "...", "e": "AQAB" }
     *   ]
     * }
     * </pre>
     */
    public synchronized void loadJwkSetJson(String jwksJson) {
        Objects.requireNonNull(jwksJson, "jwksJson must not be null");
        Map<String, Object> root = SimpleJsonParser.parseObject(jwksJson);

        Object keysObj = root.get("keys");
        if (!(keysObj instanceof List<?> rawList)) {
            throw new IllegalArgumentException("JWKS JSON must contain a 'keys' array");
        }

        List<JwkKey> parsedKeys = new ArrayList<>();
        Map<String, JwkKey> parsedMap = new HashMap<>();

        for (Object item : rawList) {
            if (item instanceof Map<?, ?> mapItem) {
                @SuppressWarnings("unchecked")
                Map<String, Object> keyMapItem = (Map<String, Object>) mapItem;
                JwkKey jwkKey = JwkKey.fromMap(keyMapItem);
                parsedKeys.add(jwkKey);
                if (jwkKey.kid() != null) {
                    parsedMap.put(jwkKey.kid(), jwkKey);
                }
            }
        }

        this.keyMap.clear();
        this.keyMap.putAll(parsedMap);
        this.keys.clear();
        this.keys.addAll(parsedKeys);
        this.loadedAt = Instant.now();
    }

    /**
     * Resolves the {@link PublicKey} corresponding to the provided kid.
     *
     * @param kid the key ID
     * @return the resolved {@link PublicKey}
     * @throws IllegalStateException if the JWK cache has expired
     * @throws IllegalArgumentException if the key with the given kid is not found
     */
    public PublicKey resolveKey(String kid) {
        if (isExpired()) {
            throw new IllegalStateException("JWK Set cache has expired (TTL: " + cacheTtl + ")");
        }

        JwkKey key = keyMap.get(kid);
        if (key == null) {
            throw new IllegalArgumentException("JWK key with kid '" + kid + "' not found");
        }
        return key.toPublicKey();
    }

    /**
     * Returns the {@link JwkKey} metadata for the given kid if present.
     */
    public Optional<JwkKey> findKey(String kid) {
        return Optional.ofNullable(keyMap.get(kid));
    }

    /**
     * Returns an unmodifiable list of all loaded JWK keys.
     */
    public List<JwkKey> getKeys() {
        return Collections.unmodifiableList(keys);
    }

    /**
     * Checks if the cached keys have exceeded the configured TTL.
     */
    public boolean isExpired() {
        if (loadedAt == null) {
            return true;
        }
        if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()) {
            return false;
        }
        return Instant.now().isAfter(loadedAt.plus(cacheTtl));
    }

    /**
     * Invalidates the current cache.
     */
    public synchronized void invalidateCache() {
        this.keyMap.clear();
        this.keys.clear();
        this.loadedAt = null;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    /**
     * Serializes a list of {@link JwkKey} objects into RFC 7517 JWKS JSON string.
     */
    public static String toJwksJson(List<JwkKey> keys) {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(keys.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }
}
