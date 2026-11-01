package com.msp.x509;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("X509CertificateLoader & Parser Tests")
class X509CertificateLoaderTest {

    @Test
    @DisplayName("Generates, loads, and parses an in-memory self-signed X.509 certificate")
    void testSelfSignedCertificateGenerationAndParsing() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate cert = X509CertificateLoader.createSelfSignedRsaCertificate(
                kp, "CN=Test Server, O=MSP, C=US", 30
        );

        assertNotNull(cert);
        ParsedCertificate parsed = X509CertificateLoader.parse(cert);

        assertTrue(parsed.isSelfSigned(), "Certificate must be self-signed");
        assertTrue(parsed.subjectDn().contains("CN=Test Server"));
        assertTrue(parsed.issuerDn().contains("CN=Test Server"));
        assertNotNull(parsed.serialNumber());
        assertNotNull(parsed.serialNumberHex());

        assertEquals("RSA", parsed.keyAlgorithm());
        assertEquals(2048, parsed.keySizeBits());
        assertTrue(parsed.sigAlgName().toUpperCase().contains("SHA256"));

        Instant now = Instant.now();
        assertTrue(parsed.isValidAt(now));
        assertFalse(parsed.isExpired(now));
        assertTrue(parsed.isExpired(now.plusSeconds(35 * 86400L)));
    }

    @Test
    @DisplayName("PEM serialization and re-parsing round-trip")
    void testPemRoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate cert = X509CertificateLoader.createSelfSignedRsaCertificate(
                kp, "CN=RoundTrip CA, O=MSP", 10
        );

        String pem = X509CertificateLoader.toPem(cert);
        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----"));
        assertTrue(pem.contains("-----END CERTIFICATE-----"));

        X509Certificate reloaded = X509CertificateLoader.loadFromPem(pem);
        assertEquals(cert.getSerialNumber(), reloaded.getSerialNumber());
        assertEquals(cert.getSubjectX500Principal(), reloaded.getSubjectX500Principal());

        ParsedCertificate parsed = X509CertificateLoader.parsePem(pem);
        assertTrue(parsed.subjectDn().contains("CN=RoundTrip CA"));
    }

    @Test
    @DisplayName("Certificate chain validation succeeds for valid trust anchor")
    void testChainValidation() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate rootCert = X509CertificateLoader.createSelfSignedRsaCertificate(
                kp, "CN=Root CA, O=MSP", 365
        );

        // Chain with root cert
        List<X509Certificate> chain = List.of(rootCert);

        ValidationResult success = X509CertificateLoader.verifyChain(
                chain, kp.getPublic(), Instant.now()
        );
        assertTrue(success.valid(), "Valid chain must pass verification");

        // Verify with wrong public key
        KeyPair wrongKp = kpg.generateKeyPair();
        ValidationResult failure = X509CertificateLoader.verifyChain(
                chain, wrongKp.getPublic(), Instant.now()
        );
        assertFalse(failure.valid(), "Verification with wrong key must fail");
    }

    @Test
    @DisplayName("Chain validation fails when certificate is checked outside validity window")
    void testChainValidationExpired() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate cert = X509CertificateLoader.createSelfSignedRsaCertificate(
                kp, "CN=Expiring Cert, O=MSP", 5
        );

        // Check 10 days in future
        Instant futureDate = Instant.now().plusSeconds(10 * 86400L);
        ValidationResult res = X509CertificateLoader.verifyChain(List.of(cert), kp.getPublic(), futureDate);
        assertFalse(res.valid());
        assertTrue(res.errorMessage().contains("expired"));
    }

    @Test
    @DisplayName("Handles invalid inputs gracefully")
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> X509CertificateLoader.loadFromPem(null));
        assertThrows(IllegalArgumentException.class, () -> X509CertificateLoader.loadFromPem("   "));
        assertThrows(IllegalArgumentException.class, () -> X509CertificateLoader.loadFromDer(new byte[0]));
        assertFalse(X509CertificateLoader.verifyChain(List.of(), null, Instant.now()).valid());
    }
}
