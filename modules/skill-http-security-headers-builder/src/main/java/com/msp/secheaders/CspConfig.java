package com.msp.secheaders;

import java.util.*;

/**
 * Content-Security-Policy (CSP) configuration and builder.
 * W3C CSP Level 3.
 */
public final class CspConfig {

    private final Map<String, List<String>> directives;

    private CspConfig(Map<String, List<String>> directives) {
        this.directives = Collections.unmodifiableMap(new LinkedHashMap<>(directives));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CspConfig strict() {
        return builder()
                .defaultSrc("'none'")
                .scriptSrc("'self'")
                .styleSrc("'self'")
                .imgSrc("'self'")
                .connectSrc("'self'")
                .fontSrc("'self'")
                .objectSrc("'none'")
                .baseUri("'none'")
                .formAction("'self'")
                .frameAncestors("'none'")
                .upgradeInsecureRequests()
                .build();
    }

    public static CspConfig parse(String cspString) {
        Objects.requireNonNull(cspString, "cspString cannot be null");
        Builder b = builder();
        String[] parts = cspString.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            String directive = tokens[0];
            String[] sources = Arrays.copyOfRange(tokens, 1, tokens.length);
            b.directive(directive, sources);
        }
        return b.build();
    }

    public Map<String, List<String>> directives() {
        return directives;
    }

    public Optional<List<String>> getDirective(String directive) {
        return Optional.ofNullable(directives.get(directive.toLowerCase(Locale.ROOT)));
    }

    public String toHeaderValue() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : directives.entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            first = false;
            sb.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                sb.append(" ").append(String.join(" ", entry.getValue()));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toHeaderValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CspConfig cspConfig = (CspConfig) o;
        return Objects.equals(directives, cspConfig.directives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(directives);
    }

    public static final class Builder {
        private final Map<String, List<String>> directives = new LinkedHashMap<>();

        public Builder directive(String name, String... sources) {
            Objects.requireNonNull(name, "Directive name cannot be null");
            String key = name.trim().toLowerCase(Locale.ROOT);
            List<String> list = directives.computeIfAbsent(key, k -> new ArrayList<>());
            if (sources != null) {
                for (String src : sources) {
                    if (src != null && !src.isBlank()) {
                        list.add(src.trim());
                    }
                }
            }
            return this;
        }

        public Builder defaultSrc(String... sources) {
            return directive("default-src", sources);
        }

        public Builder scriptSrc(String... sources) {
            return directive("script-src", sources);
        }

        public Builder styleSrc(String... sources) {
            return directive("style-src", sources);
        }

        public Builder imgSrc(String... sources) {
            return directive("img-src", sources);
        }

        public Builder connectSrc(String... sources) {
            return directive("connect-src", sources);
        }

        public Builder fontSrc(String... sources) {
            return directive("font-src", sources);
        }

        public Builder objectSrc(String... sources) {
            return directive("object-src", sources);
        }

        public Builder mediaSrc(String... sources) {
            return directive("media-src", sources);
        }

        public Builder frameSrc(String... sources) {
            return directive("frame-src", sources);
        }

        public Builder frameAncestors(String... sources) {
            return directive("frame-ancestors", sources);
        }

        public Builder baseUri(String... sources) {
            return directive("base-uri", sources);
        }

        public Builder formAction(String... sources) {
            return directive("form-action", sources);
        }

        public Builder reportUri(String uri) {
            return directive("report-uri", uri);
        }

        public Builder upgradeInsecureRequests() {
            return directive("upgrade-insecure-requests");
        }

        public Builder blockAllMixedContent() {
            return directive("block-all-mixed-content");
        }

        public CspConfig build() {
            return new CspConfig(directives);
        }
    }
}
