package com.msp.secheaders;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Immutable collection of HTTP security response headers.
 */
public final class SecurityHeaders {

    public static final String HEADER_HSTS = "Strict-Transport-Security";
    public static final String HEADER_CSP = "Content-Security-Policy";
    public static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    public static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";
    public static final String HEADER_COOP = "Cross-Origin-Opener-Policy";
    public static final String HEADER_COEP = "Cross-Origin-Embedder-Policy";
    public static final String HEADER_CORP = "Cross-Origin-Resource-Policy";

    private final Map<String, String> headers;
    private final HstsConfig hstsConfig;
    private final CspConfig cspConfig;
    private final XFrameOptions frameOptions;
    private final ReferrerPolicy referrerPolicy;
    private final PermissionsPolicyConfig permissionsPolicyConfig;
    private final CoopPolicy coopPolicy;
    private final CoepPolicy coepPolicy;
    private final CorpPolicy corpPolicy;

    SecurityHeaders(
            Map<String, String> headers,
            HstsConfig hstsConfig,
            CspConfig cspConfig,
            XFrameOptions frameOptions,
            ReferrerPolicy referrerPolicy,
            PermissionsPolicyConfig permissionsPolicyConfig,
            CoopPolicy coopPolicy,
            CoepPolicy coepPolicy,
            CorpPolicy corpPolicy
    ) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.hstsConfig = hstsConfig;
        this.cspConfig = cspConfig;
        this.frameOptions = frameOptions;
        this.referrerPolicy = referrerPolicy;
        this.permissionsPolicyConfig = permissionsPolicyConfig;
        this.coopPolicy = coopPolicy;
        this.coepPolicy = coepPolicy;
        this.corpPolicy = corpPolicy;
    }

    public static SecurityHeadersBuilder builder() {
        return new SecurityHeadersBuilder();
    }

    public Map<String, String> toHeadersMap() {
        return headers;
    }

    public void applyTo(BiConsumer<String, String> headerConsumer) {
        Objects.requireNonNull(headerConsumer, "headerConsumer cannot be null");
        headers.forEach(headerConsumer);
    }

    public Optional<String> getHeader(String headerName) {
        if (headerName == null) return Optional.empty();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public boolean hasHeader(String headerName) {
        return getHeader(headerName).isPresent();
    }

    public HstsConfig hstsConfig() {
        return hstsConfig;
    }

    public CspConfig cspConfig() {
        return cspConfig;
    }

    public XFrameOptions frameOptions() {
        return frameOptions;
    }

    public ReferrerPolicy referrerPolicy() {
        return referrerPolicy;
    }

    public PermissionsPolicyConfig permissionsPolicyConfig() {
        return permissionsPolicyConfig;
    }

    public CoopPolicy coopPolicy() {
        return coopPolicy;
    }

    public CoepPolicy coepPolicy() {
        return coepPolicy;
    }

    public CorpPolicy corpPolicy() {
        return corpPolicy;
    }

    @Override
    public String toString() {
        return "SecurityHeaders" + headers;
    }
}
