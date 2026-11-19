package com.msp.digestauth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an RFC 7616 {@code Authorization: Digest ...} client credential header.
 */
public record DigestCredentials(
        String username,
        String realm,
        String nonce,
        String uri,
        String response,
        DigestAlgorithm algorithm,
        Optional<String> cnonce,
        Optional<String> opaque,
        Qop qop,
        Optional<String> nc,
        boolean userhash
) {
    public DigestCredentials {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(realm, "realm must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(response, "response must not be null");
        algorithm = algorithm == null ? DigestAlgorithm.MD5 : algorithm;
        cnonce = cnonce == null ? Optional.empty() : cnonce;
        opaque = opaque == null ? Optional.empty() : opaque;
        qop = qop == null ? Qop.NONE : qop;
        nc = nc == null ? Optional.empty() : nc;
    }

    /**
     * Parses an {@code Authorization} header value into {@link DigestCredentials}.
     */
    public static DigestCredentials parse(String headerValue) {
        Map<String, String> params = DigestHeaderCodec.parseParams(headerValue);
        String username = requireParam(params, "username");
        String realm = requireParam(params, "realm");
        String nonce = requireParam(params, "nonce");
        String uri = requireParam(params, "uri");
        String response = requireParam(params, "response");

        DigestAlgorithm algorithm = params.containsKey("algorithm")
                ? DigestAlgorithm.parse(params.get("algorithm"))
                : DigestAlgorithm.MD5;

        Qop qop = params.containsKey("qop")
                ? Qop.parse(params.get("qop"))
                : Qop.NONE;

        boolean userhash = "true".equalsIgnoreCase(params.get("userhash"));

        return new DigestCredentials(
                username,
                realm,
                nonce,
                uri,
                response,
                algorithm,
                Optional.ofNullable(params.get("cnonce")),
                Optional.ofNullable(params.get("opaque")),
                qop,
                Optional.ofNullable(params.get("nc")),
                userhash
        );
    }

    private static String requireParam(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null) {
            throw new DigestAuthException("Missing required '" + key + "' parameter in Authorization header");
        }
        return v;
    }

    /**
     * Serializes these credentials into an RFC 7616 {@code Authorization} header value.
     */
    public String toHeaderValue() {
        List<String> parts = new ArrayList<>();
        parts.add("username=" + DigestHeaderCodec.quote(username));
        parts.add("realm=" + DigestHeaderCodec.quote(realm));
        parts.add("nonce=" + DigestHeaderCodec.quote(nonce));
        parts.add("uri=" + DigestHeaderCodec.quote(uri));
        parts.add("algorithm=" + algorithm.token());
        if (qop != Qop.NONE) {
            parts.add("qop=" + qop.token());
            nc.ifPresent(n -> parts.add("nc=" + n));
            cnonce.ifPresent(c -> parts.add("cnonce=" + DigestHeaderCodec.quote(c)));
        }
        parts.add("response=" + DigestHeaderCodec.quote(response));
        opaque.ifPresent(o -> parts.add("opaque=" + DigestHeaderCodec.quote(o)));
        if (userhash) {
            parts.add("userhash=true");
        }
        return "Digest " + String.join(", ", parts);
    }
}
