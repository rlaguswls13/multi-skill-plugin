package com.msp.secheaders;

import java.util.*;
import java.util.function.Consumer;

/**
 * Fluent builder for assembling secure HTTP response headers.
 */
public final class SecurityHeadersBuilder {

    private HstsConfig hsts;
    private CspConfig csp;
    private XFrameOptions frameOptions;
    private boolean contentTypeOptionsNosniff = true;
    private ReferrerPolicy referrerPolicy;
    private PermissionsPolicyConfig permissionsPolicy;
    private CoopPolicy coop;
    private CoepPolicy coep;
    private CorpPolicy corp;
    private final Map<String, String> customHeaders = new LinkedHashMap<>();

    public SecurityHeadersBuilder() {
    }

    /**
     * Strict preset: Production-ready defense-in-depth header suite.
     */
    public static SecurityHeaders strict() {
        return new SecurityHeadersBuilder()
                .hsts(HstsConfig.defaultStrict())
                .csp(CspConfig.strict())
                .xFrameOptions(XFrameOptions.DENY)
                .contentTypeOptionsNosniff(true)
                .referrerPolicy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                .permissionsPolicy(PermissionsPolicyConfig.defaultRestrictive())
                .coop(CoopPolicy.SAME_ORIGIN)
                .coep(CoepPolicy.REQUIRE_CORP)
                .corp(CorpPolicy.SAME_ORIGIN)
                .build();
    }

    /**
     * Modern REST / JSON API preset: prevents framing, MIME sniffing, cross-origin leaks.
     */
    public static SecurityHeaders api() {
        return new SecurityHeadersBuilder()
                .hsts(HstsConfig.oneYear())
                .csp(CspConfig.builder().defaultSrc("'none'").frameAncestors("'none'").build())
                .xFrameOptions(XFrameOptions.DENY)
                .contentTypeOptionsNosniff(true)
                .referrerPolicy(ReferrerPolicy.NO_REFERRER)
                .corp(CorpPolicy.SAME_ORIGIN)
                .build();
    }

    /**
     * Relaxed web application preset: allows same-origin framing and permissive CSP.
     */
    public static SecurityHeaders relaxed() {
        return new SecurityHeadersBuilder()
                .hsts(HstsConfig.oneYear())
                .csp(CspConfig.builder().defaultSrc("'self'").frameAncestors("'self'").build())
                .xFrameOptions(XFrameOptions.SAMEORIGIN)
                .contentTypeOptionsNosniff(true)
                .referrerPolicy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                .build();
    }

    public SecurityHeadersBuilder hsts(HstsConfig hstsConfig) {
        this.hsts = hstsConfig;
        return this;
    }

    public SecurityHeadersBuilder hsts(long maxAgeSeconds, boolean includeSubDomains, boolean preload) {
        return hsts(new HstsConfig(maxAgeSeconds, includeSubDomains, preload));
    }

    public SecurityHeadersBuilder csp(CspConfig cspConfig) {
        this.csp = cspConfig;
        return this;
    }

    public SecurityHeadersBuilder csp(Consumer<CspConfig.Builder> consumer) {
        CspConfig.Builder builder = CspConfig.builder();
        consumer.accept(builder);
        this.csp = builder.build();
        return this;
    }

    public SecurityHeadersBuilder xFrameOptions(XFrameOptions frameOptions) {
        this.frameOptions = frameOptions;
        return this;
    }

    public SecurityHeadersBuilder contentTypeOptionsNosniff(boolean enable) {
        this.contentTypeOptionsNosniff = enable;
        return this;
    }

    public SecurityHeadersBuilder referrerPolicy(ReferrerPolicy referrerPolicy) {
        this.referrerPolicy = referrerPolicy;
        return this;
    }

    public SecurityHeadersBuilder permissionsPolicy(PermissionsPolicyConfig permissionsPolicy) {
        this.permissionsPolicy = permissionsPolicy;
        return this;
    }

    public SecurityHeadersBuilder permissionsPolicy(Consumer<PermissionsPolicyConfig.Builder> consumer) {
        PermissionsPolicyConfig.Builder builder = PermissionsPolicyConfig.builder();
        consumer.accept(builder);
        this.permissionsPolicy = builder.build();
        return this;
    }

    public SecurityHeadersBuilder coop(CoopPolicy coop) {
        this.coop = coop;
        return this;
    }

    public SecurityHeadersBuilder coep(CoepPolicy coep) {
        this.coep = coep;
        return this;
    }

    public SecurityHeadersBuilder corp(CorpPolicy corp) {
        this.corp = corp;
        return this;
    }

    public SecurityHeadersBuilder customHeader(String name, String value) {
        Objects.requireNonNull(name, "Header name cannot be null");
        Objects.requireNonNull(value, "Header value cannot be null");
        this.customHeaders.put(name.trim(), value.trim());
        return this;
    }

    public SecurityHeaders build() {
        Map<String, String> headers = new LinkedHashMap<>();

        if (hsts != null) {
            headers.put(SecurityHeaders.HEADER_HSTS, hsts.toHeaderValue());
        }
        if (csp != null) {
            headers.put(SecurityHeaders.HEADER_CSP, csp.toHeaderValue());
        }
        if (frameOptions != null) {
            headers.put(SecurityHeaders.HEADER_X_FRAME_OPTIONS, frameOptions.headerValue());
        }
        if (contentTypeOptionsNosniff) {
            headers.put(SecurityHeaders.HEADER_X_CONTENT_TYPE_OPTIONS, "nosniff");
        }
        if (referrerPolicy != null) {
            headers.put(SecurityHeaders.HEADER_REFERRER_POLICY, referrerPolicy.headerValue());
        }
        if (permissionsPolicy != null) {
            headers.put(SecurityHeaders.HEADER_PERMISSIONS_POLICY, permissionsPolicy.toHeaderValue());
        }
        if (coop != null) {
            headers.put(SecurityHeaders.HEADER_COOP, coop.headerValue());
        }
        if (coep != null) {
            headers.put(SecurityHeaders.HEADER_COEP, coep.headerValue());
        }
        if (corp != null) {
            headers.put(SecurityHeaders.HEADER_CORP, corp.headerValue());
        }

        headers.putAll(customHeaders);

        return new SecurityHeaders(
                headers,
                hsts,
                csp,
                frameOptions,
                referrerPolicy,
                permissionsPolicy,
                coop,
                coep,
                corp
        );
    }
}
