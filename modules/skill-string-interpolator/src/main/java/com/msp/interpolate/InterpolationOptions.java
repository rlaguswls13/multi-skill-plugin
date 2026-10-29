package com.msp.interpolate;

/**
 * Configuration options for string template interpolation.
 *
 * @param prefix                 Placeholder starting delimiter (default "${").
 * @param suffix                 Placeholder ending delimiter (default "}").
 * @param missingStrategy        Strategy for missing or null variables (PRESERVE, EMPTY, THROW).
 * @param enableNestedProperties Whether to enable dot notation property navigation (e.g. "user.address.city").
 */
public record InterpolationOptions(
        String prefix,
        String suffix,
        MissingStrategy missingStrategy,
        boolean enableNestedProperties
) {
    public static final InterpolationOptions DEFAULT = new InterpolationOptions(
            "${",
            "}",
            MissingStrategy.PRESERVE,
            true
    );

    public static final InterpolationOptions EMPTY_ON_MISSING = new InterpolationOptions(
            "${",
            "}",
            MissingStrategy.EMPTY,
            true
    );

    public static final InterpolationOptions THROW_ON_MISSING = new InterpolationOptions(
            "${",
            "}",
            MissingStrategy.THROW,
            true
    );

    public InterpolationOptions {
        prefix = (prefix == null || prefix.isEmpty()) ? "${" : prefix;
        suffix = (suffix == null || suffix.isEmpty()) ? "}" : suffix;
        missingStrategy = (missingStrategy != null) ? missingStrategy : MissingStrategy.PRESERVE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String prefix = "${";
        private String suffix = "}";
        private MissingStrategy missingStrategy = MissingStrategy.PRESERVE;
        private boolean enableNestedProperties = true;

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder suffix(String suffix) {
            this.suffix = suffix;
            return this;
        }

        public Builder missingStrategy(MissingStrategy missingStrategy) {
            this.missingStrategy = missingStrategy;
            return this;
        }

        public Builder enableNestedProperties(boolean enableNestedProperties) {
            this.enableNestedProperties = enableNestedProperties;
            return this;
        }

        public InterpolationOptions build() {
            return new InterpolationOptions(prefix, suffix, missingStrategy, enableNestedProperties);
        }
    }
}
