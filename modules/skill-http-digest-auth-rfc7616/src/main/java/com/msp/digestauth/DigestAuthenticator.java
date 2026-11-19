package com.msp.digestauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Stateless, thread-safe RFC 7616 HTTP Digest Access Authentication calculator and verifier.
 *
 * <p>Supports {@code SHA-256}, {@code SHA-256-sess}, {@code MD5}, and {@code MD5-sess},
 * with {@code qop=auth}, {@code qop=auth-int}, or legacy RFC 2069 mode ({@link Qop#NONE}),
 * plus {@code userhash=true} and constant-time response verification.</p>
 */
public final class DigestAuthenticator {

    private static final HexFormat HEX = HexFormat.of();

    private DigestAuthenticator() {
    }

    /**
     * Computes {@code HA1} according to RFC 7616 §3.4.2.
     */
    public static String computeHa1(
            String username,
            String realm,
            String password,
            DigestAlgorithm algorithm,
            String nonce,
            String cnonce
    ) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(realm, "realm must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");

        String base = hashHex(algorithm, username + ":" + realm + ":" + password);
        if (algorithm.isSession()) {
            if (nonce == null || nonce.isEmpty()) {
                throw new DigestAuthException("nonce is required for session algorithm " + algorithm.token());
            }
            if (cnonce == null || cnonce.isEmpty()) {
                throw new DigestAuthException("cnonce is required for session algorithm " + algorithm.token());
            }
            return hashHex(algorithm, base + ":" + nonce + ":" + cnonce);
        }
        return base;
    }

    /**
     * Computes {@code HA2} according to RFC 7616 §3.4.3.
     */
    public static String computeHa2(
            String method,
            String uri,
            DigestAlgorithm algorithm,
            Qop qop,
            byte[] entityBody
    ) {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Qop effectiveQop = qop == null ? Qop.NONE : qop;

        if (effectiveQop == Qop.AUTH_INT) {
            byte[] body = entityBody == null ? new byte[0] : entityBody;
            String bodyHash = hashHex(algorithm, body);
            return hashHex(algorithm, method + ":" + uri + ":" + bodyHash);
        }
        return hashHex(algorithm, method + ":" + uri);
    }

    /**
     * Computes the final Digest {@code response} value according to RFC 7616 §3.4.1.
     */
    public static String computeResponse(
            String ha1,
            String nonce,
            String nc,
            String cnonce,
            Qop qop,
            String ha2,
            DigestAlgorithm algorithm
    ) {
        Objects.requireNonNull(ha1, "ha1 must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        Objects.requireNonNull(ha2, "ha2 must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Qop effectiveQop = qop == null ? Qop.NONE : qop;

        if (effectiveQop == Qop.NONE) {
            return hashHex(algorithm, ha1 + ":" + nonce + ":" + ha2);
        }
        if (nc == null || nc.isBlank()) {
            throw new DigestAuthException("nc is required when qop is " + effectiveQop.token());
        }
        if (cnonce == null || cnonce.isBlank()) {
            throw new DigestAuthException("cnonce is required when qop is " + effectiveQop.token());
        }
        return hashHex(algorithm, ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + effectiveQop.token() + ":" + ha2);
    }

    /**
     * Computes RFC 7616 §3.4.4 {@code userhash = H(username ":" realm)}.
     */
    public static String computeUserhash(String username, String realm, DigestAlgorithm algorithm) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(realm, "realm must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        return hashHex(algorithm, username + ":" + realm);
    }

    /**
     * Formats an integer nonce count into an RFC 7616 8-character lowercase hex string (e.g. {@code 00000001}).
     */
    public static String formatNc(int nonceCount) {
        if (nonceCount <= 0) {
            throw new DigestAuthException("nonceCount must be positive: " + nonceCount);
        }
        return String.format(Locale.ROOT, "%08x", nonceCount);
    }

    /**
     * Generates client {@link DigestCredentials} in response to a server {@link DigestChallenge}.
     */
    public static DigestCredentials authorize(
            DigestChallenge challenge,
            String username,
            String password,
            String method,
            String uri,
            String cnonce,
            int nonceCount
    ) {
        return authorize(challenge, username, password, method, uri, cnonce, nonceCount, null);
    }

    /**
     * Generates client {@link DigestCredentials} in response to a server {@link DigestChallenge},
     * optionally using {@code entityBody} if {@code auth-int} is selected.
     */
    public static DigestCredentials authorize(
            DigestChallenge challenge,
            String username,
            String password,
            String method,
            String uri,
            String cnonce,
            int nonceCount,
            byte[] entityBody
    ) {
        Objects.requireNonNull(challenge, "challenge must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");

        Qop selectedQop = selectQop(challenge);
        DigestAlgorithm alg = challenge.algorithm();
        String nc = selectedQop == Qop.NONE ? null : formatNc(nonceCount);

        String ha1 = computeHa1(username, challenge.realm(), password, alg, challenge.nonce(), cnonce);
        String ha2 = computeHa2(method, uri, alg, selectedQop, entityBody);
        String response = computeResponse(ha1, challenge.nonce(), nc, cnonce, selectedQop, ha2, alg);

        String wireUsername = challenge.userhash()
                ? computeUserhash(username, challenge.realm(), alg)
                : username;

        return new DigestCredentials(
                wireUsername,
                challenge.realm(),
                challenge.nonce(),
                uri,
                response,
                alg,
                selectedQop == Qop.NONE ? Optional.empty() : Optional.ofNullable(cnonce),
                challenge.opaque(),
                selectedQop,
                Optional.ofNullable(nc),
                challenge.userhash()
        );
    }

    /**
     * Verifies client {@link DigestCredentials} using constant-time digest comparison.
     */
    public static boolean verify(
            DigestCredentials credentials,
            String expectedRealm,
            String expectedUsername,
            String password,
            String method,
            String expectedUri
    ) {
        return verify(credentials, expectedRealm, expectedUsername, password, method, expectedUri, null);
    }

    /**
     * Verifies client {@link DigestCredentials} (including optional {@code auth-int} body)
     * using constant-time digest comparison.
     */
    public static boolean verify(
            DigestCredentials credentials,
            String expectedRealm,
            String expectedUsername,
            String password,
            String method,
            String expectedUri,
            byte[] entityBody
    ) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        Objects.requireNonNull(expectedRealm, "expectedRealm must not be null");
        Objects.requireNonNull(expectedUsername, "expectedUsername must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(expectedUri, "expectedUri must not be null");

        if (!expectedRealm.equals(credentials.realm())) {
            return false;
        }
        if (!expectedUri.equals(credentials.uri())) {
            return false;
        }

        DigestAlgorithm alg = credentials.algorithm();
        if (credentials.userhash()) {
            String expectedUserhash = computeUserhash(expectedUsername, expectedRealm, alg);
            if (!constantTimeEqualsHex(expectedUserhash, credentials.username())) {
                return false;
            }
        } else if (!expectedUsername.equals(credentials.username())) {
            return false;
        }

        Qop qop = credentials.qop();
        String cnonce = credentials.cnonce().orElse(null);
        String nc = credentials.nc().orElse(null);
        if (qop != Qop.NONE && (cnonce == null || cnonce.isBlank() || nc == null || nc.isBlank())) {
            return false;
        }
        if (alg.isSession() && (cnonce == null || cnonce.isBlank())) {
            return false;
        }

        String ha1 = computeHa1(expectedUsername, expectedRealm, password, alg, credentials.nonce(), cnonce);
        String ha2 = computeHa2(method, expectedUri, alg, qop, entityBody);
        String expectedResponse = computeResponse(ha1, credentials.nonce(), nc, cnonce, qop, ha2, alg);

        return constantTimeEqualsHex(expectedResponse, credentials.response());
    }

    private static Qop selectQop(DigestChallenge challenge) {
        if (challenge.qopOptions().contains(Qop.AUTH)) {
            return Qop.AUTH;
        }
        if (challenge.qopOptions().contains(Qop.AUTH_INT)) {
            return Qop.AUTH_INT;
        }
        return Qop.NONE;
    }

    private static boolean constantTimeEqualsHex(String expectedLowerHex, String actualHex) {
        if (actualHex == null) {
            return false;
        }
        byte[] a = expectedLowerHex.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] b = actualHex.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static String hashHex(DigestAlgorithm algorithm, String input) {
        return hashHex(algorithm, input.getBytes(StandardCharsets.UTF_8));
    }

    private static String hashHex(DigestAlgorithm algorithm, byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm.jcaName());
            return HEX.formatHex(md.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing standard JCA algorithm: " + algorithm.jcaName(), e);
        }
    }
}
