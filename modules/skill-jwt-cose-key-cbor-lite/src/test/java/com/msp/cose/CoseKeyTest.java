package com.msp.cose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CoseKeyTest {

    @Test
    @DisplayName("Parse standard WebAuthn ES256 (P-256) COSE Key and generate java.security.PublicKey")
    void testParseWebAuthnEs256Key() throws Exception {
        // Authentic WebAuthn Passkey P-256 COSE Key
        // Map: 1: 2 (kty=EC2), 3: -7 (alg=ES256), -1: 1 (crv=P-256), -2: x (32 bytes), -3: y (32 bytes)
        // Generated standard P-256 coordinates:
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        ECPublicKey ecPub = (ECPublicKey) kp.getPublic();

        byte[] xBytes = toFixedByteArray(ecPub.getW().getAffineX().toByteArray(), 32);
        byte[] yBytes = toFixedByteArray(ecPub.getW().getAffineY().toByteArray(), 32);

        Map<Long, CborValue> params = Map.of(
                CoseKey.LABEL_KTY, new CborValue.IntegerVal(CoseKey.KTY_EC2),
                CoseKey.LABEL_ALG, new CborValue.IntegerVal(CoseKey.ALG_ES256),
                CoseKey.LABEL_CRV, new CborValue.IntegerVal(CoseKey.CRV_P256),
                CoseKey.LABEL_X, new CborValue.ByteStringVal(xBytes),
                CoseKey.LABEL_Y, new CborValue.ByteStringVal(yBytes)
        );

        CoseKey key = CoseKey.of(params);
        byte[] cborBytes = key.encode();

        // Parse from CBOR bytes
        CoseKey parsed = CoseKey.parse(cborBytes);
        assertEquals(2L, parsed.getKeyType().orElseThrow());
        assertEquals(-7L, parsed.getAlgorithm().orElseThrow());
        assertEquals(1L, parsed.getCurve().orElseThrow());
        assertArrayEquals(xBytes, parsed.getXCoordinate().orElseThrow());
        assertArrayEquals(yBytes, parsed.getYCoordinate().orElseThrow());

        // Convert to PublicKey and verify signature
        PublicKey reconstructedPub = parsed.toPublicKey();
        assertInstanceOf(ECPublicKey.class, reconstructedPub);

        byte[] data = "WebAuthn Authenticator Assertion".getBytes();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(kp.getPrivate());
        signer.update(data);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(reconstructedPub);
        verifier.update(data);
        assertTrue(verifier.verify(signature));
    }

    @Test
    @DisplayName("Parse RSA COSE Key and generate RSAPublicKey")
    void testParseRsaKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey rsaPub = (RSAPublicKey) kp.getPublic();

        byte[] nBytes = rsaPub.getModulus().toByteArray();
        byte[] eBytes = rsaPub.getPublicExponent().toByteArray();

        Map<Long, CborValue> params = Map.of(
                CoseKey.LABEL_KTY, new CborValue.IntegerVal(CoseKey.KTY_RSA),
                CoseKey.LABEL_ALG, new CborValue.IntegerVal(CoseKey.ALG_RS256),
                CoseKey.LABEL_RSA_N, new CborValue.ByteStringVal(nBytes),
                CoseKey.LABEL_RSA_E, new CborValue.ByteStringVal(eBytes)
        );

        CoseKey key = CoseKey.of(params);
        byte[] encoded = key.encode();

        CoseKey parsed = CoseKey.parse(encoded);
        assertEquals(3L, parsed.getKeyType().orElseThrow());
        assertEquals(-257L, parsed.getAlgorithm().orElseThrow());

        PublicKey reconstructedPub = parsed.toPublicKey();
        assertInstanceOf(RSAPublicKey.class, reconstructedPub);

        RSAPublicKey reconstructedRsa = (RSAPublicKey) reconstructedPub;
        assertEquals(rsaPub.getModulus(), reconstructedRsa.getModulus());
        assertEquals(rsaPub.getPublicExponent(), reconstructedRsa.getPublicExponent());
    }

    @Test
    @DisplayName("Parse OKP Ed25519 COSE Key")
    void testParseEd25519Key() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        // Extract 32-byte public key from X.509 encoding
        byte[] encoded = kp.getPublic().getEncoded();
        byte[] rawX = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, rawX, 0, 32);

        Map<Long, CborValue> params = Map.of(
                CoseKey.LABEL_KTY, new CborValue.IntegerVal(CoseKey.KTY_OKP),
                CoseKey.LABEL_ALG, new CborValue.IntegerVal(CoseKey.ALG_EDDSA),
                CoseKey.LABEL_CRV, new CborValue.IntegerVal(CoseKey.CRV_ED25519),
                CoseKey.LABEL_X, new CborValue.ByteStringVal(rawX)
        );

        CoseKey key = CoseKey.of(params);
        byte[] cbor = key.encode();

        CoseKey parsed = CoseKey.parse(cbor);
        PublicKey pubKey = parsed.toPublicKey();
        assertNotNull(pubKey);
        assertEquals(kp.getPublic().getAlgorithm(), pubKey.getAlgorithm());

        // Verify signature with reconstructed key
        byte[] msg = "Passkey sign request".getBytes();
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(msg);
        byte[] signature = sig.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pubKey);
        verifier.update(msg);
        assertTrue(verifier.verify(signature));
    }

    @Test
    @DisplayName("CBOR Reader and Writer primitives roundtrip")
    void testCborPrimitives() {
        CborValue val = new CborValue.MapVal(Map.of(
                new CborValue.IntegerVal(1), new CborValue.TextStringVal("hello"),
                new CborValue.IntegerVal(-7), new CborValue.IntegerVal(42),
                new CborValue.IntegerVal(100), new CborValue.BooleanVal(true),
                new CborValue.TextStringVal("items"), new CborValue.ArrayVal(List.of(
                        new CborValue.IntegerVal(10),
                        new CborValue.IntegerVal(20),
                        new CborValue.NullVal()
                ))
        ));

        byte[] encoded = CborWriter.encode(val);
        CborValue decoded = CborReader.read(encoded);
        assertEquals(val, decoded);
    }

    @Test
    @DisplayName("Hex parsing and error conditions")
    void testHexAndErrors() {
        assertThrows(CborParseException.class, () -> CoseKey.parse(new byte[]{0x01, 0x02}));

        // Empty map is a map but missing kty
        byte[] emptyMapCbor = new byte[]{(byte) 0xa0};
        CoseKey key = CoseKey.parse(emptyMapCbor);
        assertTrue(key.getKeyType().isEmpty());
        assertThrows(GeneralSecurityException.class, key::toPublicKey);
    }

    private static byte[] toFixedByteArray(byte[] src, int len) {
        if (src.length == len) return src;
        byte[] dst = new byte[len];
        if (src.length > len) {
            // Strip leading zero bytes (e.g. BigInteger sign byte)
            System.arraycopy(src, src.length - len, dst, 0, len);
        } else {
            System.arraycopy(src, 0, dst, len - src.length, src.length);
        }
        return dst;
    }
}
