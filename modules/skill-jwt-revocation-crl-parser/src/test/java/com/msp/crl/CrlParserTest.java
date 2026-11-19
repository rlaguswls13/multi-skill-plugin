package com.msp.crl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CRLReason;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CrlParserTest {

    @Test
    @DisplayName("Parse valid X.509 CRL DER and PEM data and query revocation status")
    void testParseCrlDerAndPem() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant thisUpdate = now.minus(1, ChronoUnit.DAYS);
        Instant nextUpdate = now.plus(7, ChronoUnit.DAYS);

        BigInteger rev1 = new BigInteger("1001");
        BigInteger rev2 = new BigInteger("A5F12", 16);

        var specs = List.of(
                new CrlTestUtil.RevokedCertSpec(rev1, thisUpdate, 1), // KEY_COMPROMISE (1)
                new CrlTestUtil.RevokedCertSpec(rev2, thisUpdate, 4)  // SUPERSEDED (4)
        );

        byte[] derCrl = CrlTestUtil.generateDerCrl(keyPair, "Test CA Authority", thisUpdate, nextUpdate, specs);
        String pemCrl = CrlTestUtil.toPem(derCrl);

        // 1. Test DER parsing
        CrlSnapshot derSnapshot = CrlParser.parse(derCrl);
        assertEquals(2, derSnapshot.revokedCount());
        assertTrue(derSnapshot.getIssuerDN().contains("Test CA Authority"));
        assertEquals("SHA256withRSA", derSnapshot.getSignatureAlgorithm());
        assertFalse(derSnapshot.isExpired(now));

        // Revocation checks
        assertTrue(derSnapshot.isRevoked(rev1));
        assertTrue(derSnapshot.isRevoked("1001")); // decimal/hex check
        assertTrue(derSnapshot.isRevoked(rev2));
        assertTrue(derSnapshot.isRevoked("A5F12"));
        assertTrue(derSnapshot.isRevoked("0xA5F12"));
        assertFalse(derSnapshot.isRevoked(new BigInteger("99999")));
        assertFalse(derSnapshot.isRevoked("FFFF"));

        // Revocation info details
        Optional<RevocationInfo> info1 = derSnapshot.getRevocationInfo(rev1);
        assertTrue(info1.isPresent());
        assertEquals(CRLReason.KEY_COMPROMISE, info1.get().reason());

        Optional<RevocationInfo> info2 = derSnapshot.getRevocationInfo(rev2);
        assertTrue(info2.isPresent());
        assertEquals(CRLReason.SUPERSEDED, info2.get().reason());

        // 2. Test PEM parsing
        CrlSnapshot pemSnapshot = CrlParser.parsePem(pemCrl);
        assertEquals(2, pemSnapshot.revokedCount());
        assertTrue(pemSnapshot.isRevoked(rev1));
        assertTrue(pemSnapshot.isRevoked(rev2));

        // Expiry verification
        assertTrue(pemSnapshot.isExpired(now.plus(10, ChronoUnit.DAYS)));
        assertFalse(pemSnapshot.isExpired(now.plus(2, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("Error handling on invalid PEM or corrupt DER data")
    void testErrorHandling() {
        assertThrows(CrlParseException.class, () -> CrlParser.parse(new byte[0]));
        assertThrows(CrlParseException.class, () -> CrlParser.parsePem("NOT_PEM_CONTENT"));
        assertThrows(CrlParseException.class, () -> CrlParser.parse(new byte[]{0x30, 0x05, 0x00, 0x00, 0x00}));
    }
}
