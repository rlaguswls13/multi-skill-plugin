package com.msp.sessbind;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Context encapsulating client connection environment attributes for cryptographic binding.
 */
public record ClientFingerprintContext(
        String ipAddress,
        IpBindingMode ipBindingMode,
        String userAgent,
        String tlsCertThumbprint,
        String tlsChannelBinding,
        String deviceKeyId,
        Map<String, String> extraAttributes
) {
    public ClientFingerprintContext {
        if (ipBindingMode == null) {
            ipBindingMode = IpBindingMode.SUBNET;
        }
        if (extraAttributes == null) {
            extraAttributes = Map.of();
        } else {
            extraAttributes = Collections.unmodifiableMap(new HashMap<>(extraAttributes));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String ipAddress;
        private IpBindingMode ipBindingMode = IpBindingMode.SUBNET;
        private String userAgent;
        private String tlsCertThumbprint;
        private String tlsChannelBinding;
        private String deviceKeyId;
        private final Map<String, String> extraAttributes = new HashMap<>();

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder ipBindingMode(IpBindingMode ipBindingMode) {
            this.ipBindingMode = ipBindingMode;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder tlsCertThumbprint(String tlsCertThumbprint) {
            this.tlsCertThumbprint = tlsCertThumbprint;
            return this;
        }

        public Builder tlsChannelBinding(String tlsChannelBinding) {
            this.tlsChannelBinding = tlsChannelBinding;
            return this;
        }

        public Builder deviceKeyId(String deviceKeyId) {
            this.deviceKeyId = deviceKeyId;
            return this;
        }

        public Builder attribute(String key, String value) {
            this.extraAttributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            if (attributes != null) {
                this.extraAttributes.putAll(attributes);
            }
            return this;
        }

        public ClientFingerprintContext build() {
            return new ClientFingerprintContext(
                    ipAddress,
                    ipBindingMode,
                    userAgent,
                    tlsCertThumbprint,
                    tlsChannelBinding,
                    deviceKeyId,
                    extraAttributes
            );
        }
    }
}
