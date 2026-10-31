package com.msp.urlcanon;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration options for URL canonicalization.
 */
public record CanonicalizeOptions(
        boolean removeTrackingParams,
        boolean sortQueryParams,
        boolean normalizePath,
        boolean stripDefaultPort,
        boolean lowercaseSchemeAndHost,
        boolean stripFragment,
        Set<String> extraTrackingParams
) {
    public static final CanonicalizeOptions DEFAULT = new CanonicalizeOptions(
            true,
            true,
            true,
            true,
            true,
            true,
            Collections.emptySet()
    );

    public CanonicalizeOptions {
        Objects.requireNonNull(extraTrackingParams, "extraTrackingParams cannot be null");
        extraTrackingParams = Collections.unmodifiableSet(new HashSet<>(extraTrackingParams));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean removeTrackingParams = true;
        private boolean sortQueryParams = true;
        private boolean normalizePath = true;
        private boolean stripDefaultPort = true;
        private boolean lowercaseSchemeAndHost = true;
        private boolean stripFragment = true;
        private Set<String> extraTrackingParams = new HashSet<>();

        public Builder removeTrackingParams(boolean removeTrackingParams) {
            this.removeTrackingParams = removeTrackingParams;
            return this;
        }

        public Builder sortQueryParams(boolean sortQueryParams) {
            this.sortQueryParams = sortQueryParams;
            return this;
        }

        public Builder normalizePath(boolean normalizePath) {
            this.normalizePath = normalizePath;
            return this;
        }

        public Builder stripDefaultPort(boolean stripDefaultPort) {
            this.stripDefaultPort = stripDefaultPort;
            return this;
        }

        public Builder lowercaseSchemeAndHost(boolean lowercaseSchemeAndHost) {
            this.lowercaseSchemeAndHost = lowercaseSchemeAndHost;
            return this;
        }

        public Builder stripFragment(boolean stripFragment) {
            this.stripFragment = stripFragment;
            return this;
        }

        public Builder addExtraTrackingParam(String paramName) {
            if (paramName != null && !paramName.isBlank()) {
                this.extraTrackingParams.add(paramName.trim().toLowerCase());
            }
            return this;
        }

        public CanonicalizeOptions build() {
            return new CanonicalizeOptions(
                    removeTrackingParams,
                    sortQueryParams,
                    normalizePath,
                    stripDefaultPort,
                    lowercaseSchemeAndHost,
                    stripFragment,
                    extraTrackingParams
            );
        }
    }
}
