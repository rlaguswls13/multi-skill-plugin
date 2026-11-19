package com.msp.jwsdetached;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwsDetachedSignerVerifierTest {

    private static final byte[] SECRET = "super-secret-banking-webhook-key-32bytes!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PAYLOAD = "{\"amount\":150000,\"currency\":\"KRW\",\"txId\":\"TX-9981\"}"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void testHmacRfc7797UnencodedDetachedSignAndVerify() {
        DetachedJwsToken token = JwsDetachedSignerVerifier.signHmac(
                JwsAlgorithm.HS256,
                SECRET,
                PAYLOAD,
                false, // RFC 7797 b64=false
                "webhook-key-1",
                Map.of("iat", "1700000000")
        );

        String compact = token.toCompactDetached();
        assertTrue(compact.contains(".."));
        assertFalse( token.b64EncodedPayload());
        assertTrue(token.decodedHeaderJson().contains("\"b64\":false"));
        assertTrue(token.decodedHeaderJson().contains("\"crit\":[\"b64\"]"));

        assertTrue(JwsDetachedSignerVerifier.verifyHmac(compact, SECRET, PAYLOAD));
        // Tampered payload must fail
        byte[] tampered = "{\"amount\":999999,\"currency\":\"KRW\",\"txId\":\"TX-9981\"}"
                .getBytes(StandardCharsets.UTF_8);
        assertFalse(JwsDetachedSignerVerifier.verifyHmac(compact, SECRET, tampered));
    }

    @Test
    void testHmacStandardBase64UrlDetachedSignAndVerify() {
        DetachedJwsToken token = JwsDetachedSignerVerifier.signHmac(
                JwsAlgorithm.HS512,
                SECRET,
                PAYLOAD,
                true, // Standard RFC 7515 Appendix F
                "key-hs512",
                Map.of()
        );

        String compact = token.toCompactDetached();
        assertTrue(token.b64EncodedPayload());
        assertFalse(token.decodedHeaderJson().contains("\"b64\":false"));

        assertTrue(JwsDetachedSignerVerifier.verifyHmac(compact, SECRET, PAYLOAD));
    }

    @Test
    void testRsaRs256DetachedSignAndStreamingVerify() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // Multi-chunk payload (30 KB) to exercise streaming
        byte[] largePayload = new byte[30_000];
        for (int i = 0; i < largePayload.length; i++) {
            largePayload[i] = (byte) (i % 251);
        }

        DetachedJwsToken token = JwsDetachedSignerVerifier.signAsymmetricStream(
                JwsAlgorithm.RS256,
                kp.getPrivate(),
                new ByteArrayInputStream(largePayload),
                false,
                "rsa-bank-key",
                Map.of()
        );

        assertTrue(JwsDetachedSignerVerifier.verifyAsymmetricStream(
                token.toCompactDetached(),
                kp.getPublic(),
                new ByteArrayInputStream(largePayload)
        ));

        largePayload[15_000] ^= 0x01;
        assertFalse(JwsDetachedSignerVerifier.verifyAsymmetricStream(
                token.toCompactDetached(),
                kp.getPublic(),
                new ByteArrayInputStream(largePayload)
        ));
    }

    @Test
    void testEcdsaEs256DetachedSignAndVerify() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        DetachedJwsToken token = JwsDetachedSignerVerifier.signAsymmetric(
                JwsAlgorithm.ES256,
                kp.getPrivate(),
                PAYLOAD,
                true,
                "ec-p256-key",
                Map.of()
        );

        // ES256 raw IEEE P1363 signature is 64 bytes (R || S)
        assertEquals(64, token.signatureBytes().length);
        assertTrue(JwsDetachedSignerVerifier.verifyAsymmetric(token.toCompactDetached(), kp.getPublic(), PAYLOAD));
    }

    @Test
    void testRfc7797CritHeaderValidationAndMalformedTokens() {
        // Header with "b64":false but missing "crit":["b64"] must be rejected per RFC 7797
        String badHeaderJson = "{\"alg\":\"HS256\",\"b64\":false}";
        String badEncodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(badHeaderJson.getBytes(StandardCharsets.UTF_8));
        String badCompact = badEncodedHeader + "..AAAA";

        assertThrows(JwsDetachedException.class, () -> DetachedJwsToken.parse(badCompact));
        assertThrows(JwsDetachedException.class, () -> DetachedJwsToken.parse("not.detached.token"));
    }
}
