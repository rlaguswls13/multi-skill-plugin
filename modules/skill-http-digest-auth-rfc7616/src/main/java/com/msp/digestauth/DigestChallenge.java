package com.msp.digestauth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents an RFC 7616 {@code WWW-Authenticate: Digest ...} server challenge.
 */
public record DigestChallenge(
        String realm,
        String nonce,
        Optional<String> opaque,
        DigestAlgorithm algorithm,
        List<Qop> qopOptions,
        boolean stale,
        Optional<String> domain,
        Optional<String> charset,
        boolean userhash
) {
    public DigestChallenge {
        Objects.requireNonNull(realm, "realm must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        if (nonce.isBlank()) {
            throw new DigestAuthException("nonce must not be blank");
        }
        opaque = opaque == null ? Optional.empty() : opaque;
        algorithm = algorithm == null ? DigestAlgorithm.SHA_256 : algorithm;
        qopOptions = qopOptions == null ? List.of() : List.copyOf(qopOptions);
        domain = domain == null ? Optional.empty() : domain;
        charset = charset == null ? Optional.empty() : charset;
    }

    public static Builder builder(String realm, String nonce) {
        return new Builder(realm, nonce);
    }

    /**
     * Parses a {@code WWW-Authenticate} header value into a {@link DigestChallenge}.
     */
    public static DigestChallenge parse(String headerValue) {
        Map<String, String> params = DigestHeaderCodec.parseParams(headerValue);
        String realm = params.get("realm");
        if (realm == null) {
            throw new DigestAuthException("Missing required 'realm' parameter in WWW-Authenticate challenge");
        }
        String nonce = params.get("nonce");
        if (nonce == null || nonce.isBlank()) {
            throw new DigestAuthException("Missing required 'nonce' parameter in WWW-Authenticate challenge");
        }
        DigestAlgorithm algorithm = params.containsKey("algorithm")
                ? DigestAlgorithm.parse(params.get("algorithm"))
                : DigestAlgorithm.MD5; // RFC 7616 §3.3 default if omitted

        List<Qop> qops = new ArrayList<>();
        String qopRaw = params.get("qop");
        if (qopRaw != null && !qopRaw.isBlank()) {
            for (String part : qopRaw.split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    qops.add(Qop.parse(p));
                }
            }
        }

        boolean stale = "true".equalsIgnoreCase(params.get("stale"));
        boolean userhash = "true".equalsIgnoreCase(params.get("userhash"));

        return new DigestChallenge(
                realm,
                nonce,
                Optional.ofNullable(params.get("opaque")),
                algorithm,
                qops,
                stale,
                Optional.ofNullable(params.get("domain")),
                Optional.ofNullable(params.get("charset")),
                userhash
        );
    }

    /**
     * Serializes this challenge into an RFC 7616 {@code WWW-Authenticate} header value.
     */
    public String toHeaderValue() {
        List<String> parts = new ArrayList<>();
        parts.add("realm=" + DigestHeaderCodec.quote(realm));
        domain.ifPresent(d -> parts.add("domain=" + DigestHeaderCodec.quote(d)));
        parts.add("nonce=" + DigestHeaderCodec.quote(nonce));
        opaque.ifPresent(o -> parts.add("opaque=" + DigestHeaderCodec.quote(o)));
        if (stale) {
            parts.add("stale=true");
        }
        parts.add("algorithm=" + algorithm.token());
        if (!qopOptions.isEmpty()) {
            String qopStr = qopOptions.stream()
                    .filter(q -> q != Qop.NONE)
                    .map(Qop::token)
                    .collect(Collectors.joining(","));
            if (!qopStr.isEmpty()) {
                parts.add("qop=" + DigestHeaderCodec.quote(qopStr));
            }
        }
        charset.ifPresent(c -> parts.add("charset=" + c));
        if (userhash) {
            parts.add("userhash=true");
        }
        return "Digest " + String.join(", ", parts);
    }

    public static final class Builder {
        private final String realm;
        private final String nonce;
        private String opaque;
        private DigestAlgorithm algorithm = DigestAlgorithm.SHA_256;
        private List<Qop> qopOptions = List.of(Qop.AUTH);
        private boolean stale;
        private String domain;
        private String charset;
        private boolean userhash;

        private Builder(String realm, String nonce) {
            this.realm = Objects.requireNonNull(realm, "realm must not be null");
            this.nonce = Objects.requireNonNull(nonce, "nonce must not be null");
        }

        public Builder opaque(String opaque) {
            this.opaque = opaque;
            return this;
        }

        public Builder algorithm(DigestAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
            return this;
        }

        public Builder qopOptions(Qop... qops) {
            this.qopOptions = List.of(qops);
            return this;
        }

        public Builder qopOptions(List<Qop> qops) {
            this.qopOptions = List.copyOf(qops);
            return this;
        }

        public Builder stale(boolean stale) {
            this.stale = stale;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder charset(String charset) {
            this.charset = charset;
            return this;
        }

        public Builder userhash(boolean userhash) {
            this.userhash = userhash;
            return this;
        }

        public DigestChallenge build() {
            return new DigestChallenge(
                    realm,
                    nonce,
                    Optional.ofNullable(opaque),
                    algorithm,
                    qopOptions,
                    stale,
                    Optional.ofNullable(domain),
                    Optional.ofNullable(charset),
                    userhash
            );
        }
    }
}
