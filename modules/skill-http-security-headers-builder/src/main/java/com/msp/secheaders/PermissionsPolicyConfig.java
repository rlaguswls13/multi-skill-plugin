package com.msp.secheaders;

import java.util.*;

/**
 * Permissions-Policy header configuration.
 * W3C Permissions Policy.
 */
public final class PermissionsPolicyConfig {

    private final Map<String, List<String>> policies;

    private PermissionsPolicyConfig(Map<String, List<String>> policies) {
        this.policies = Collections.unmodifiableMap(new LinkedHashMap<>(policies));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PermissionsPolicyConfig defaultRestrictive() {
        return builder()
                .geolocation()
                .camera()
                .microphone()
                .payment()
                .usb()
                .build();
    }

    public Map<String, List<String>> policies() {
        return policies;
    }

    public String toHeaderValue() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : policies.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(entry.getKey()).append("=");
            List<String> allowlist = entry.getValue();
            if (allowlist.isEmpty()) {
                sb.append("()");
            } else if (allowlist.size() == 1 && allowlist.get(0).equals("*")) {
                sb.append("*");
            } else {
                sb.append("(").append(String.join(" ", allowlist)).append(")");
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
        PermissionsPolicyConfig that = (PermissionsPolicyConfig) o;
        return Objects.equals(policies, that.policies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policies);
    }

    public static final class Builder {
        private final Map<String, List<String>> policies = new LinkedHashMap<>();

        public Builder feature(String name, String... allowlist) {
            Objects.requireNonNull(name, "Feature name cannot be null");
            String key = name.trim().toLowerCase(Locale.ROOT);
            List<String> list = policies.computeIfAbsent(key, k -> new ArrayList<>());
            if (allowlist != null) {
                for (String item : allowlist) {
                    if (item != null && !item.isBlank()) {
                        list.add(item.trim());
                    }
                }
            }
            return this;
        }

        // Disabled by default (empty allowlist = '()')
        public Builder geolocation(String... allowlist) {
            return feature("geolocation", allowlist);
        }

        public Builder camera(String... allowlist) {
            return feature("camera", allowlist);
        }

        public Builder microphone(String... allowlist) {
            return feature("microphone", allowlist);
        }

        public Builder payment(String... allowlist) {
            return feature("payment", allowlist);
        }

        public Builder usb(String... allowlist) {
            return feature("usb", allowlist);
        }

        public Builder fullscreen(String... allowlist) {
            return feature("fullscreen", allowlist);
        }

        public Builder displayCapture(String... allowlist) {
            return feature("display-capture", allowlist);
        }

        public Builder accelerometer(String... allowlist) {
            return feature("accelerometer", allowlist);
        }

        public Builder gyroscope(String... allowlist) {
            return feature("gyroscope", allowlist);
        }

        public Builder magnetometer(String... allowlist) {
            return feature("magnetometer", allowlist);
        }

        public PermissionsPolicyConfig build() {
            return new PermissionsPolicyConfig(policies);
        }
    }
}
