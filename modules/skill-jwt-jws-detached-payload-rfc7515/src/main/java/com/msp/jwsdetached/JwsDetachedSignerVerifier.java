package com.msp.jwsdetached;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 7515 Appendix F &amp; RFC 7797 ({@code b64=false}) JWS Detached Content Signer and Streaming Verifier.
 * <p>
 * Produces and verifies {@code <base64url-header>..<base64url-signature>} tokens over arbitrary payloads
 * (both standard Base64URL-encoded payloads and unencoded streaming payloads) using HMAC, RSA, and ECDSA.
 */
public final class JwsDetachedSignerVerifier {

    private static final int STREAM_BUFFER_SIZE = 8192;

    private JwsDetachedSignerVerifier() {
    }

    /**
     * Signs a byte array payload with an HMAC secret and produces a compact detached JWS token.
     *
     * @param algorithm          HMAC algorithm (HS256, HS384, HS512)
     * @param secret             shared secret key bytes
     * @param payload            raw payload bytes
     * @param b64EncodedPayload  true for RFC 7515 standard base64url payload signing, false for RFC 7797 unencoded payload
     * @param keyId              optional key ID ("kid"), may be null
     * @param extraHeaders       optional extra string header parameters
     */
    public static DetachedJwsToken signHmac(
            JwsAlgorithm algorithm,
            byte[] secret,
            byte[] payload,
            boolean b64EncodedPayload,
            String keyId,
            Map<String, String> extraHeaders) {
        Objects.requireNonNull(payload, "payload must not be null");
        return signHmacStream(algorithm, secret, new ByteArrayInputStream(payload), b64EncodedPayload, keyId, extraHeaders);
    }

    /**
     * Signs an {@link InputStream} payload in streaming fashion with an HMAC secret.
     */
    public static DetachedJwsToken signHmacStream(
            JwsAlgorithm algorithm,
            byte[] secret,
            InputStream payloadStream,
            boolean b64EncodedPayload,
            String keyId,
            Map<String, String> extraHeaders) {
        validateHmacAlgorithm(algorithm);
        Objects.requireNonNull(secret, "HMAC secret must not be null");
        Objects.requireNonNull(payloadStream, "payloadStream must not be null");

        String encodedHeader = DetachedJwsToken.encodeProtectedHeader(algorithm, b64EncodedPayload, keyId, extraHeaders);
        try {
            Mac mac = Mac.getInstance(algorithm.jcaName());
            mac.init(new SecretKeySpec(secret, algorithm.jcaName()));
            feedSigningInput(encodedHeader, payloadStream, b64EncodedPayload, mac::update);
            byte[] sigBytes = mac.doFinal();
            String encodedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
            return new DetachedJwsToken(algorithm, b64EncodedPayload, keyId, extraHeaders, encodedHeader, encodedSig);
        } catch (GeneralSecurityException | IOException e) {
            throw new JwsDetachedException("Failed to compute HMAC detached JWS signature", e);
        }
    }

    /**
     * Signs a byte array payload with an asymmetric {@link PrivateKey} (RS256 or ES256).
     */
    public static DetachedJwsToken signAsymmetric(
            JwsAlgorithm algorithm,
            PrivateKey privateKey,
            byte[] payload,
            boolean b64EncodedPayload,
            String keyId,
            Map<String, String> extraHeaders) {
        Objects.requireNonNull(payload, "payload must not be null");
        return signAsymmetricStream(
                algorithm, privateKey, new ByteArrayInputStream(payload), b64EncodedPayload, keyId, extraHeaders);
    }

    /**
     * Signs an {@link InputStream} payload in streaming fashion with an asymmetric {@link PrivateKey} (RS256 or ES256).
     */
    public static DetachedJwsToken signAsymmetricStream(
            JwsAlgorithm algorithm,
            PrivateKey privateKey,
            InputStream payloadStream,
            boolean b64EncodedPayload,
            String keyId,
            Map<String, String> extraHeaders) {
        validateAsymmetricAlgorithm(algorithm);
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(payloadStream, "payloadStream must not be null");

        String encodedHeader = DetachedJwsToken.encodeProtectedHeader(algorithm, b64EncodedPayload, keyId, extraHeaders);
        try {
            Signature signature = Signature.getInstance(algorithm.jcaName());
            signature.initSign(privateKey);
            feedSigningInput(encodedHeader, payloadStream, b64EncodedPayload, (buf, off, len) -> {
                try {
                    signature.update(buf, off, len);
                } catch (GeneralSecurityException ex) {
                    throw new JwsDetachedException("Failed updating asymmetric signature", ex);
                }
            });
            byte[] sigBytes = signature.sign();
            String encodedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
            return new DetachedJwsToken(algorithm, b64EncodedPayload, keyId, extraHeaders, encodedHeader, encodedSig);
        } catch (GeneralSecurityException | IOException e) {
            throw new JwsDetachedException("Failed to compute asymmetric detached JWS signature", e);
        }
    }

    /**
     * Verifies a compact detached JWS token ({@code <header>..<signature>}) against a byte array payload using an HMAC secret.
     */
    public static boolean verifyHmac(String compactDetachedJws, byte[] secret, byte[] payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return verifyHmacStream(compactDetachedJws, secret, new ByteArrayInputStream(payload));
    }

    /**
     * Verifies a compact detached JWS token against a streaming {@link InputStream} payload using an HMAC secret.
     */
    public static boolean verifyHmacStream(String compactDetachedJws, byte[] secret, InputStream payloadStream) {
        DetachedJwsToken token = DetachedJwsToken.parse(compactDetachedJws);
        validateHmacAlgorithm(token.algorithm());
        Objects.requireNonNull(secret, "HMAC secret must not be null");
        Objects.requireNonNull(payloadStream, "payloadStream must not be null");

        try {
            Mac mac = Mac.getInstance(token.algorithm().jcaName());
            mac.init(new SecretKeySpec(secret, token.algorithm().jcaName()));
            feedSigningInput(token.encodedHeader(), payloadStream, token.b64EncodedPayload(), mac::update);
            byte[] expected = mac.doFinal();
            byte[] actual = token.signatureBytes();
            return MessageDigest.isEqual(expected, actual);
        } catch (GeneralSecurityException | IOException e) {
            throw new JwsDetachedException("Failed to verify HMAC detached JWS signature", e);
        }
    }

    /**
     * Verifies a compact detached JWS token ({@code <header>..<signature>}) against a byte array payload using a {@link PublicKey}.
     */
    public static boolean verifyAsymmetric(String compactDetachedJws, PublicKey publicKey, byte[] payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return verifyAsymmetricStream(compactDetachedJws, publicKey, new ByteArrayInputStream(payload));
    }

    /**
     * Verifies a compact detached JWS token against a streaming {@link InputStream} payload using a {@link PublicKey}.
     */
    public static boolean verifyAsymmetricStream(
            String compactDetachedJws,
            PublicKey publicKey,
            InputStream payloadStream) {
        DetachedJwsToken token = DetachedJwsToken.parse(compactDetachedJws);
        validateAsymmetricAlgorithm(token.algorithm());
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(payloadStream, "payloadStream must not be null");

        try {
            Signature signature = Signature.getInstance(token.algorithm().jcaName());
            signature.initVerify(publicKey);
            feedSigningInput(token.encodedHeader(), payloadStream, token.b64EncodedPayload(), (buf, off, len) -> {
                try {
                    signature.update(buf, off, len);
                } catch (GeneralSecurityException ex) {
                    throw new JwsDetachedException("Failed updating asymmetric verifier", ex);
                }
            });
            return signature.verify(token.signatureBytes());
        } catch (GeneralSecurityException | IOException e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface ByteSink {
        void update(byte[] buf, int offset, int length);
    }

    /**
     * Streams {@code ASCII(encodedHeader) || '.' || payloadRepresentation} directly into the cryptographic sink
     * without materializing full multi-megabyte payloads in memory.
     */
    private static void feedSigningInput(
            String encodedHeader,
            InputStream payloadStream,
            boolean b64EncodedPayload,
            ByteSink sink) throws IOException {
        byte[] headerPrefix = (encodedHeader + ".").getBytes(StandardCharsets.US_ASCII);
        sink.update(headerPrefix, 0, headerPrefix.length);

        if (!b64EncodedPayload) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = payloadStream.read(buffer)) != -1) {
                if (read > 0) {
                    sink.update(buffer, 0, read);
                }
            }
        } else {
            // Stream through Base64.getUrlEncoder().withoutPadding().wrap(...) into the sink
            OutputStream sinkStream = new OutputStream() {
                @Override
                public void write(int b) {
                    sink.update(new byte[]{(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    if (len > 0) {
                        sink.update(b, off, len);
                    }
                }
            };
            try (OutputStream b64Out = Base64.getUrlEncoder().withoutPadding().wrap(sinkStream)) {
                byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                int read;
                while ((read = payloadStream.read(buffer)) != -1) {
                    if (read > 0) {
                        b64Out.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static void validateHmacAlgorithm(JwsAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        if (!algorithm.isHmac()) {
            throw new JwsDetachedException("Expected HMAC algorithm (HS256/HS384/HS512) but got: " + algorithm);
        }
    }

    private static void validateAsymmetricAlgorithm(JwsAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        if (algorithm.isHmac()) {
            throw new JwsDetachedException("Expected asymmetric algorithm (RS256/ES256) but got: " + algorithm);
        }
    }
}
