package com.msp.secheaders;

import java.util.Objects;

/**
 * HTTP Strict-Transport-Security (HSTS) configuration.
 * RFC 6797.
 */
public record HstsConfig(
        long maxAgeSeconds,
        boolean includeSubDomains,
        boolean preload
) {
    public static final long DEFAULT_MAX_AGE_SECONDS = 31536000L; // 1 year

    public HstsConfig {
        if (maxAgeSeconds < 0) {
            throw new IllegalArgumentException("maxAgeSeconds must be non-negative");
        }
    }

    public static HstsConfig defaultStrict() {
        return new HstsConfig(DEFAULT_MAX_AGE_SECONDS * 2, true, true); // 2 years, subdomains, preload
    }

    public static HstsConfig oneYear() {
        return new HstsConfig(DEFAULT_MAX_AGE_SECONDS, true, false);
    }

    public static HstsConfig of(long maxAgeSeconds, boolean includeSubDomains, boolean preload) {
        return new HstsConfig(maxAgeSeconds, includeSubDomains, preload);
    }

    public String toHeaderValue() {
        StringBuilder sb = new StringBuilder("max-age=").append(maxAgeSeconds);
        if (includeSubDomains) {
            sb.append("; includeSubDomains");
        }
        if (preload) {
            sb.append("; preload");
        }
        return sb.toString();
    }
}
