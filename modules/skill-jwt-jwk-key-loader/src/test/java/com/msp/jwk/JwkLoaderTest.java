package com.msp.jwk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwkLoader Tests")
class JwkLoaderTest {

    @Test
    @DisplayName("JwkKeyType enum values and parsing")
    void testJwkKeyType() {
        assertEquals(JwkKeyType.RSA, JwkKeyType.fromString("RSA"));
        assertEquals(JwkKeyType.RSA, JwkKeyType.fromString("rsa"));
        assertEquals(JwkKeyType.EC, JwkKeyType.fromString("EC"));
        assertEquals(JwkKeyType.OCT, JwkKeyType.fromString("OCT"));
        assertThrows(IllegalArgumentException.class, () -> JwkKeyType.fromString("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> JwkKeyType.fromString(null));
    }

    @Test
    @DisplayName("Reconstruct RSA Public Key from JWK and verify digital signature")
    void testLoadRsaJwkAndVerifySignature() throws Exception {
        // 1. Generate standard RSA key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey originalPubKey = (RSAPublicKey) kp.getPublic();
        RSAPrivateKey originalPrivKey = (RSAPrivateKey) kp.getPrivate();

        // 2. Base64Url encode modulus and exponent (BigInteger to unsigned byte array)
        String nBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(originalPubKey.getModulus()));
        String eBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(originalPubKey.getPublicExponent()));

        // 3. Construct JWK JSON string
        String jwkJson = String.format("""
                {
                  "kty": "RSA",
                  "use": "sig",
                  "alg": "RS256",
                  "kid": "test-rsa-key-1",
                  "n": "%s",
                  "e": "%s"
                }
                """, nBase64Url, eBase64Url);

        // 4. Load JWK
        JwkPublicKey jwkPublicKey = JwkLoader.loadJwk(jwkJson);
        assertNotNull(jwkPublicKey);
        assertEquals("test-rsa-key-1", jwkPublicKey.kid());
        assertEquals("RSA", jwkPublicKey.kty());
        assertEquals("sig", jwkPublicKey.use());
        assertEquals("RS256", jwkPublicKey.alg());
        assertTrue(jwkPublicKey.publicKey() instanceof RSAPublicKey);

        RSAPublicKey reconstructedKey = (RSAPublicKey) jwkPublicKey.publicKey();
        assertEquals(originalPubKey.getModulus(), reconstructedKey.getModulus());
        assertEquals(originalPubKey.getPublicExponent(), reconstructedKey.getPublicExponent());

        // 5. Verify cryptographic signature verification using reconstructed key
        byte[] payload = "Hello, MSP JWT Security!".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(originalPrivKey);
        signer.update(payload);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(reconstructedKey);
        verifier.update(payload);
        assertTrue(verifier.verify(signature), "Reconstructed RSA public key must successfully verify private key signature");
    }

    @Test
    @DisplayName("Load JWK Set (JWKS) containing multiple keys")
    void testLoadJwks() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        KeyPair kp1 = kpg.generateKeyPair();
        KeyPair kp2 = kpg.generateKeyPair();

        RSAPublicKey pub1 = (RSAPublicKey) kp1.getPublic();
        RSAPublicKey pub2 = (RSAPublicKey) kp2.getPublic();

        String n1 = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(pub1.getModulus()));
        String e1 = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(pub1.getPublicExponent()));

        String n2 = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(pub2.getModulus()));
        String e2 = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(pub2.getPublicExponent()));

        String jwksJson = String.format("""
                {
                  "keys": [
                    {
                      "kty": "RSA",
                      "kid": "key-primary",
                      "use": "sig",
                      "n": "%s",
                      "e": "%s"
                    },
                    {
                      "kty": "RSA",
                      "kid": "key-secondary",
                      "use": "enc",
                      "n": "%s",
                      "e": "%s"
                    }
                  ]
                }
                """, n1, e1, n2, e2);

        Map<String, JwkPublicKey> jwksMap = JwkLoader.loadJwks(jwksJson);
        assertEquals(2, jwksMap.size());
        assertTrue(jwksMap.containsKey("key-primary"));
        assertTrue(jwksMap.containsKey("key-secondary"));

        assertEquals("key-primary", jwksMap.get("key-primary").kid());
        assertEquals("sig", jwksMap.get("key-primary").use());
        assertEquals("key-secondary", jwksMap.get("key-secondary").kid());
        assertEquals("enc", jwksMap.get("key-secondary").use());
    }

    @Test
    @DisplayName("Invalid JWK and JWKS JSON inputs throw appropriate exceptions")
    void testInvalidInputs() {
        assertThrows(NullPointerException.class, () -> JwkLoader.loadJwk(null));
        assertThrows(NullPointerException.class, () -> JwkLoader.loadJwks(null));
        assertThrows(IllegalArgumentException.class, () -> JwkLoader.loadJwk(""));
        assertThrows(IllegalArgumentException.class, () -> JwkLoader.loadJwk("not-a-json"));

        // Missing kty
        assertThrows(IllegalArgumentException.class, () -> JwkLoader.loadJwk("{\"kid\":\"k1\"}"));

        // Missing modulus or exponent
        assertThrows(IllegalArgumentException.class, () -> JwkLoader.loadJwk("{\"kty\":\"RSA\",\"kid\":\"k1\"}"));
        assertThrows(IllegalArgumentException.class, () -> JwkLoader.loadJwk("{\"kty\":\"RSA\",\"n\":\"abc\"}"));

        // JWKS missing keys array
        assertThrows(IllegalArgumentException.class, () -> JwkLoader.loadJwks("{\"other\":\"field\"}"));

        // Unsupported key type
        assertThrows(UnsupportedOperationException.class, () -> JwkLoader.loadJwk("{\"kty\":\"OCT\",\"kid\":\"k1\"}"));
    }

    /**
     * Converts BigInteger to unsigned byte array (stripping any leading sign byte 0x00 if present).
     */
    private static byte[] toUnsignedByteArray(java.math.BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
