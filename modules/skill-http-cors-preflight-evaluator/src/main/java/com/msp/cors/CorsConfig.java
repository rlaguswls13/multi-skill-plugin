package com.msp.cors;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration options for evaluating CORS requests according to the W3C Fetch/CORS specification.
 */
public final class CorsConfig {

    public static final String WILDCARD = "*";

    private final Set<String> allowedOrigins;
    private final List<Pattern> allowedOriginPatterns;
    private final Set<String> allowedMethods;
    private final Set<String> allowedHeaders;
    private final Set<String> exposedHeaders;
    private final boolean allowCredentials;
    private final Duration maxAge;
    private final boolean allowPrivateNetwork;

    private CorsConfig(Builder builder) {
        this.allowedOrigins = Collections.unmodifiableSet(new LinkedHashSet<>(builder.allowedOrigins));
        this.allowedOriginPatterns = Collections.unmodifiableList(new ArrayList<>(builder.allowedOriginPatterns));
        this.allowedMethods = Collections.unmodifiableSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER) {{
            addAll(builder.allowedMethods);
        }});
        this.allowedHeaders = Collections.unmodifiableSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER) {{
            addAll(builder.allowedHeaders);
        }});
        this.exposedHeaders = Collections.unmodifiableSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER) {{
            addAll(builder.exposedHeaders);
        }});
        this.allowCredentials = builder.allowCredentials;
        this.maxAge = builder.maxAge;
        this.allowPrivateNetwork = builder.allowPrivateNetwork;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Permissive configuration allowing all origins, standard methods, all headers, credentials true, and 1 hour max age.
     */
    public static CorsConfig permissive() {
        return builder()
                .allowAllOrigins()
                .allowMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")
                .allowAllHeaders()
                .allowCredentials(true)
                .maxAge(Duration.ofHours(1))
                .build();
    }

    /**
     * Restrictive configuration allowing only same-origin or explicitly specified origins, safe methods only.
     */
    public static CorsConfig restrictive() {
        return builder()
                .allowMethods("GET", "HEAD")
                .allowCredentials(false)
                .maxAge(Duration.ofMinutes(10))
                .build();
    }

    public Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public List<Pattern> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public Set<String> getAllowedMethods() {
        return allowedMethods;
    }

    public Set<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public Set<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public boolean isAllowPrivateNetwork() {
        return allowPrivateNetwork;
    }

    public boolean isAllOriginsAllowed() {
        return allowedOrigins.contains(WILDCARD);
    }

    public boolean isAllHeadersAllowed() {
        return allowedHeaders.contains(WILDCARD);
    }

    public boolean isOriginAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        if (isAllOriginsAllowed()) {
            return true;
        }
        if (allowedOrigins.contains(origin)) {
            return true;
        }
        for (Pattern pattern : allowedOriginPatterns) {
            if (pattern.matcher(origin).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean isMethodAllowed(String method) {
        if (method == null || method.isBlank()) {
            return false;
        }
        if (allowedMethods.contains(WILDCARD)) {
            return true;
        }
        return allowedMethods.contains(method.trim());
    }

    public boolean isHeaderAllowed(String header) {
        if (header == null || header.isBlank()) {
            return true;
        }
        if (isAllHeadersAllowed()) {
            return true;
        }
        return allowedHeaders.contains(header.trim());
    }

    public static final class Builder {
        private final Set<String> allowedOrigins = new LinkedHashSet<>();
        private final List<Pattern> allowedOriginPatterns = new ArrayList<>();
        private final Set<String> allowedMethods = new LinkedHashSet<>();
        private final Set<String> allowedHeaders = new LinkedHashSet<>();
        private final Set<String> exposedHeaders = new LinkedHashSet<>();
        private boolean allowCredentials = false;
        private Duration maxAge = Duration.ofSeconds(1800);
        private boolean allowPrivateNetwork = false;

        public Builder allowOrigin(String origin) {
            if (origin != null && !origin.isBlank()) {
                this.allowedOrigins.add(origin.trim());
            }
            return this;
        }

        public Builder allowOrigins(String... origins) {
            if (origins != null) {
                for (String o : origins) {
                    allowOrigin(o);
                }
            }
            return this;
        }

        public Builder allowOrigins(Collection<String> origins) {
            if (origins != null) {
                for (String o : origins) {
                    allowOrigin(o);
                }
            }
            return this;
        }

        public Builder allowAllOrigins() {
            this.allowedOrigins.add(WILDCARD);
            return this;
        }

        /**
         * Adds an origin wildcard or regex pattern.
         * Example: {@code https://*.example.com} or regex {@code ^https?://.*\.example\.org(:\d+)?$}
         */
        public Builder allowOriginPattern(String patternStr) {
            if (patternStr == null || patternStr.isBlank()) {
                return this;
            }
            if (patternStr.equals(WILDCARD)) {
                return allowAllOrigins();
            }
            String regex;
            if (patternStr.contains("*") && !patternStr.startsWith("^")) {
                // Convert glob wildcard to regex
                regex = "^" + Pattern.quote(patternStr).replace("*", "\\E.*\\Q") + "$";
            } else {
                regex = patternStr;
            }
            this.allowedOriginPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            return this;
        }

        public Builder allowOriginPattern(Pattern pattern) {
            if (pattern != null) {
                this.allowedOriginPatterns.add(pattern);
            }
            return this;
        }

        public Builder allowMethods(String... methods) {
            if (methods != null) {
                for (String m : methods) {
                    if (m != null && !m.isBlank()) {
                        this.allowedMethods.add(m.trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
            return this;
        }

        public Builder allowAllMethods() {
            this.allowedMethods.add(WILDCARD);
            return this;
        }

        public Builder allowHeaders(String... headers) {
            if (headers != null) {
                for (String h : headers) {
                    if (h != null && !h.isBlank()) {
                        this.allowedHeaders.add(h.trim());
                    }
                }
            }
            return this;
        }

        public Builder allowAllHeaders() {
            this.allowedHeaders.add(WILDCARD);
            return this;
        }

        public Builder exposeHeaders(String... headers) {
            if (headers != null) {
                for (String h : headers) {
                    if (h != null && !h.isBlank()) {
                        this.exposedHeaders.add(h.trim());
                    }
                }
            }
            return this;
        }

        public Builder allowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
            return this;
        }

        public Builder maxAge(Duration maxAge) {
            this.maxAge = Objects.requireNonNull(maxAge, "maxAge cannot be null");
            return this;
        }

        public Builder maxAge(long seconds) {
            this.maxAge = Duration.ofSeconds(seconds);
            return this;
        }

        public Builder allowPrivateNetwork(boolean allowPrivateNetwork) {
            this.allowPrivateNetwork = allowPrivateNetwork;
            return this;
        }

        public CorsConfig build() {
            if (this.allowedMethods.isEmpty()) {
                this.allowedMethods.addAll(List.of("GET", "POST", "HEAD", "OPTIONS"));
            }
            return new CorsConfig(this);
        }
    }
}
