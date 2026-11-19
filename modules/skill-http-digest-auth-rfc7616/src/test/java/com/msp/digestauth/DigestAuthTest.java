package com.msp.digestauth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DigestAuthTest {

    @Test
    void rfc7616OfficialSha256Vector() {
        // RFC 7616 Section 3.9.1 example
        String header = """
                Digest realm="http-auth@example.org", \
                qop="auth, auth-int", \
                algorithm=SHA-256, \
                nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", \
                opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS"
                """.trim();

        DigestChallenge challenge = DigestChallenge.parse(header);
        assertEquals("http-auth@example.org", challenge.realm());
        assertEquals(DigestAlgorithm.SHA_256, challenge.algorithm());
        assertEquals(List.of(Qop.AUTH, Qop.AUTH_INT), challenge.qopOptions());
        assertEquals(Optional.of("FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS"), challenge.opaque());

        DigestCredentials creds = DigestAuthenticator.authorize(
                challenge,
                "Mufasa",
                "Circle of Life",
                "GET",
                "/dir/index.html",
                "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ",
                1
        );

        assertEquals(
                "753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1",
                creds.response()
        );
        assertEquals(Optional.of("00000001"), creds.nc());
        assertTrue(DigestAuthenticator.verify(
                creds, "http-auth@example.org", "Mufasa", "Circle of Life", "GET", "/dir/index.html"));

        // Round-trip Authorization header serialization & parsing
        String authHeader = creds.toHeaderValue();
        DigestCredentials reparsed = DigestCredentials.parse(authHeader);
        assertEquals(creds, reparsed);
        assertTrue(DigestAuthenticator.verify(
                reparsed, "http-auth@example.org", "Mufasa", "Circle of Life", "GET", "/dir/index.html"));
    }

    @Test
    void rfc7616OfficialMd5Vector() {
        // RFC 7616 Section 3.9.1 MD5 example
        DigestChallenge challenge = DigestChallenge.builder(
                        "http-auth@example.org",
                        "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v")
                .algorithm(DigestAlgorithm.MD5)
                .qopOptions(Qop.AUTH, Qop.AUTH_INT)
                .opaque("FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS")
                .build();

        DigestCredentials creds = DigestAuthenticator.authorize(
                challenge,
                "Mufasa",
                "Circle of Life",
                "GET",
                "/dir/index.html",
                "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ",
                1
        );

        assertEquals("8ca523f5e9506fed4657c9700eebdbec", creds.response());
        assertTrue(DigestAuthenticator.verify(
                creds, "http-auth@example.org", "Mufasa", "Circle of Life", "GET", "/dir/index.html"));
    }

    @Test
    void sha256SessionAndUserhashFlow() {
        DigestChallenge challenge = DigestChallenge.builder("api.internal", "nonce-xyz-123")
                .algorithm(DigestAlgorithm.SHA_256_SESS)
                .qopOptions(Qop.AUTH)
                .userhash(true)
                .stale(true)
                .charset("UTF-8")
                .build();

        DigestChallenge reparsedChallenge = DigestChallenge.parse(challenge.toHeaderValue());
        assertEquals(challenge, reparsedChallenge);

        DigestCredentials creds = DigestAuthenticator.authorize(
                reparsedChallenge, "alice", "s3cr3t!", "POST", "/v1/orders", "client-nonce-99", 5);

        assertNotEquals("alice", creds.username());
        assertTrue(creds.userhash());
        assertEquals(Optional.of("00000005"), creds.nc());
        assertTrue(DigestAuthenticator.verify(
                creds, "api.internal", "alice", "s3cr3t!", "POST", "/v1/orders"));
        assertFalse(DigestAuthenticator.verify(
                creds, "api.internal", "alice", "wrong-password", "POST", "/v1/orders"));
        assertFalse(DigestAuthenticator.verify(
                creds, "api.internal", "bob", "s3cr3t!", "POST", "/v1/orders"));
    }

    @Test
    void qopAuthIntVerifiesEntityBodyIntegrity() {
        DigestChallenge challenge = DigestChallenge.builder("secure", "n-int-1")
                .algorithm(DigestAlgorithm.SHA_256)
                .qopOptions(Qop.AUTH_INT)
                .build();

        byte[] body = "{\"amount\":4200}".getBytes(StandardCharsets.UTF_8);
        DigestCredentials creds = DigestAuthenticator.authorize(
                challenge, "svc", "pass", "PUT", "/payments/1", "cn-1", 1, body);

        assertEquals(Qop.AUTH_INT, creds.qop());
        assertTrue(DigestAuthenticator.verify(
                creds, "secure", "svc", "pass", "PUT", "/payments/1", body));

        byte[] tamperedBody = "{\"amount\":9999}".getBytes(StandardCharsets.UTF_8);
        assertFalse(DigestAuthenticator.verify(
                creds, "secure", "svc", "pass", "PUT", "/payments/1", tamperedBody));
    }

    @Test
    void legacyRfc2069WithoutQop() {
        DigestChallenge challenge = DigestChallenge.builder("legacy", "legacy-nonce")
                .algorithm(DigestAlgorithm.MD5)
                .qopOptions(List.of())
                .build();

        DigestCredentials creds = DigestAuthenticator.authorize(
                challenge, "admin", "pw", "GET", "/status", null, 1);

        assertEquals(Qop.NONE, creds.qop());
        assertTrue(creds.nc().isEmpty());
        assertTrue(creds.cnonce().isEmpty());
        assertTrue(DigestAuthenticator.verify(creds, "legacy", "admin", "pw", "GET", "/status"));
    }

    @Test
    void tamperedResponseRealmOrUriFailsVerification() {
        DigestChallenge challenge = DigestChallenge.builder("realmA", "nonceA")
                .algorithm(DigestAlgorithm.SHA_256)
                .qopOptions(Qop.AUTH)
                .build();

        DigestCredentials creds = DigestAuthenticator.authorize(
                challenge, "user", "pass", "GET", "/items", "cnonceA", 1);

        String flipped = (creds.response().charAt(0) == '0' ? '1' : '0') + creds.response().substring(1);
        DigestCredentials tampered = new DigestCredentials(
                creds.username(), creds.realm(), creds.nonce(), creds.uri(), flipped,
                creds.algorithm(), creds.cnonce(), creds.opaque(), creds.qop(), creds.nc(), creds.userhash());

        assertFalse(DigestAuthenticator.verify(tampered, "realmA", "user", "pass", "GET", "/items"));
        assertFalse(DigestAuthenticator.verify(creds, "otherRealm", "user", "pass", "GET", "/items"));
        assertFalse(DigestAuthenticator.verify(creds, "realmA", "user", "pass", "GET", "/other"));
        assertFalse(DigestAuthenticator.verify(creds, "realmA", "user", "pass", "DELETE", "/items"));
    }

    @Test
    void nonceTrackerDetectsReplaysAndExpiration() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };

        NonceTracker tracker = new NonceTracker(mutableClock);
        String nonce = tracker.issueNonce(Duration.ofMinutes(5));
        assertEquals(32, nonce.length());

        assertEquals(ReplayCheckResult.VALID, tracker.validateAndAdvance(nonce, "00000001"));
        assertEquals(ReplayCheckResult.REPLAYED_NC, tracker.validateAndAdvance(nonce, "00000001"));
        assertEquals(ReplayCheckResult.VALID, tracker.validateAndAdvance(nonce, "00000002"));
        assertEquals(ReplayCheckResult.REPLAYED_NC, tracker.validateAndAdvance(nonce, "00000001"));
        assertEquals(ReplayCheckResult.INVALID_NC, tracker.validateAndAdvance(nonce, "xyz"));
        assertEquals(ReplayCheckResult.INVALID_NC, tracker.validateAndAdvance(nonce, "00000000"));
        assertEquals(ReplayCheckResult.UNKNOWN_NONCE, tracker.validateAndAdvance("nonexistent", "00000001"));

        // Advance clock past TTL
        now.set(now.get().plus(Duration.ofMinutes(6)));
        assertEquals(ReplayCheckResult.EXPIRED_NONCE, tracker.validateAndAdvance(nonce, "00000003"));
        assertEquals(0, tracker.activeCount());

        // Test purgeExpired
        tracker.registerNonce("n1", Duration.ofSeconds(10));
        tracker.registerNonce("n2", Duration.ofSeconds(60));
        now.set(now.get().plus(Duration.ofSeconds(15)));
        assertEquals(1, tracker.purgeExpired());
        assertEquals(1, tracker.activeCount());
    }

    @Test
    void malformedHeadersThrowDigestAuthException() {
        assertThrows(DigestAuthException.class, () -> DigestChallenge.parse("Basic abc123"));
        assertThrows(DigestAuthException.class, () -> DigestChallenge.parse("Digest realm=\"unterminated"));
        assertThrows(DigestAuthException.class, () -> DigestChallenge.parse("Digest nonce=\"abc\""));
        assertThrows(DigestAuthException.class, () -> DigestChallenge.parse("Digest realm=\"r\", nonce=\"n\", algorithm=SHA-1"));
        assertThrows(DigestAuthException.class, () -> DigestCredentials.parse("Digest username=\"u\""));
        assertThrows(DigestAuthException.class, () -> DigestChallenge.parse("Digest realm=\"a\", realm=\"b\", nonce=\"n\""));
    }
}
