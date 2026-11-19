package com.msp.ocsp;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OcspResponseParserTest {

    @Test
    void parsesAllNonSuccessfulStatusResponses() {
        for (OcspResponseStatus status : new OcspResponseStatus[]{
                OcspResponseStatus.MALFORMED_REQUEST,
                OcspResponseStatus.INTERNAL_ERROR,
                OcspResponseStatus.TRY_LATER,
                OcspResponseStatus.SIG_REQUIRED,
                OcspResponseStatus.UNAUTHORIZED
        }) {
            byte[] der = OcspResponseBuilder.buildStatusOnly(status);
            OcspResponse parsed = OcspResponseParser.parse(der);
            assertEquals(status, parsed.status());
            assertTrue(parsed.responseTypeOid().isEmpty());
            assertTrue(parsed.basicResponse().isEmpty());
        }
    }

    @Test
    void parsesSuccessfulBasicResponseWithGoodRevokedAndUnknownStatuses() {
        Instant producedAt = Instant.parse("2026-11-19T10:00:00Z");
        Instant thisUpdate = Instant.parse("2026-11-19T09:00:00Z");
        Instant nextUpdate = Instant.parse("2026-11-20T09:00:00Z");
        Instant revokedAt = Instant.parse("2026-10-01T12:30:45Z");

        byte[] issuerNameHash = new byte[20];
        byte[] issuerKeyHash = new byte[20];
        for (int i = 0; i < 20; i++) {
            issuerNameHash[i] = (byte) (i + 1);
            issuerKeyHash[i] = (byte) (0xA0 + i);
        }

        CertId certGood = new CertId(OcspResponseBuilder.OID_SHA1, issuerNameHash, issuerKeyHash, BigInteger.valueOf(1001));
        CertId certRevoked = new CertId(OcspResponseBuilder.OID_SHA256, issuerNameHash, issuerKeyHash, new BigInteger("98765432109876543210"));
        CertId certUnknown = new CertId(OcspResponseBuilder.OID_SHA1, issuerNameHash, issuerKeyHash, BigInteger.valueOf(404));

        byte[] responderKeyHash = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55};
        byte[] sigBytes = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        // Minimal valid DER SEQUENCE acting as an embedded certificate stub
        byte[] fakeCertDer = new byte[]{0x30, 0x03, 0x02, 0x01, 0x07};

        byte[] der = OcspResponseBuilder.builder()
                .status(OcspResponseStatus.SUCCESSFUL)
                .responderId(new ResponderId.ByKey(responderKeyHash))
                .producedAt(producedAt)
                .addSingleResponse(new SingleResponse(certGood, new CertStatus.Good(), thisUpdate, Optional.of(nextUpdate), false))
                .addSingleResponse(new SingleResponse(certRevoked, new CertStatus.Revoked(revokedAt, Optional.of(RevocationReason.KEY_COMPROMISE)), thisUpdate, Optional.empty(), false))
                .addSingleResponse(new SingleResponse(certUnknown, new CertStatus.Unknown(), thisUpdate, Optional.of(nextUpdate), false))
                .signatureAlgorithmOid(OcspResponseBuilder.OID_SHA256_WITH_RSA)
                .signature(sigBytes)
                .addCertificateDer(fakeCertDer)
                .buildDer();

        OcspResponse parsed = OcspResponseParser.parseBase64(Base64.getEncoder().encodeToString(der));
        assertEquals(OcspResponseStatus.SUCCESSFUL, parsed.status());
        assertEquals(Optional.of(OcspResponse.ID_PKIX_OCSP_BASIC), parsed.responseTypeOid());

        BasicOcspResponse basic = parsed.basicResponse().orElseThrow();
        assertEquals(OcspResponseBuilder.OID_SHA256_WITH_RSA, basic.signatureAlgorithmOid());
        assertArrayEquals(sigBytes, basic.signature());
        assertEquals(1, basic.certificates().size());
        assertArrayEquals(fakeCertDer, basic.certificates().get(0));

        ResponseData data = basic.tbsResponseData();
        assertEquals(0, data.version());
        assertEquals(producedAt, data.producedAt());
        assertEquals(new ResponderId.ByKey(responderKeyHash), data.responderId());
        assertTrue(data.rawTbsBytes().length > 0);
        assertEquals(3, data.responses().size());

        SingleResponse sr0 = data.responses().get(0);
        assertEquals(certGood, sr0.certId());
        assertInstanceOf(CertStatus.Good.class, sr0.certStatus());
        assertEquals(thisUpdate, sr0.thisUpdate());
        assertEquals(Optional.of(nextUpdate), sr0.nextUpdate());

        SingleResponse sr1 = data.responses().get(1);
        assertEquals(certRevoked, sr1.certId());
        CertStatus.Revoked rev = assertInstanceOf(CertStatus.Revoked.class, sr1.certStatus());
        assertEquals(revokedAt, rev.revocationTime());
        assertEquals(Optional.of(RevocationReason.KEY_COMPROMISE), rev.reason());
        assertTrue(sr1.nextUpdate().isEmpty());

        SingleResponse sr2 = data.responses().get(2);
        assertEquals(certUnknown, sr2.certId());
        assertInstanceOf(CertStatus.Unknown.class, sr2.certStatus());
    }

    @Test
    void supportsResponderByNameAndExplicitVersion() {
        byte[] dummyNameDer = new byte[]{0x30, 0x00}; // empty DER SEQUENCE
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        CertId certId = new CertId(OcspResponseBuilder.OID_SHA256, new byte[]{1}, new byte[]{2}, BigInteger.ONE);

        byte[] der = OcspResponseBuilder.builder()
                .version(0)
                .responderId(new ResponderId.ByName(dummyNameDer))
                .producedAt(now)
                .addSingleResponse(new SingleResponse(certId, new CertStatus.Good(), now, Optional.empty(), false))
                .buildDer();

        OcspResponse parsed = OcspResponseParser.parse(der);
        ResponseData data = parsed.basicResponse().orElseThrow().tbsResponseData();
        assertEquals(0, data.version());
        assertEquals(new ResponderId.ByName(dummyNameDer), data.responderId());
    }

    @Test
    void rejectsMalformedTruncatedOrIndefiniteDer() {
        assertThrows(OcspParseException.class, () -> OcspResponseParser.parse(new byte[0]));
        assertThrows(OcspParseException.class, () -> OcspResponseParser.parse(new byte[]{0x30, 0x05, 0x0A, 0x01}));
        // Indefinite length (0x80) is forbidden in DER
        assertThrows(OcspParseException.class, () -> OcspResponseParser.parse(new byte[]{0x30, (byte) 0x80, 0x00, 0x00}));
        // Invalid status code 4 (unused in RFC 6960)
        assertThrows(OcspParseException.class, () -> OcspResponseParser.parse(new byte[]{0x30, 0x03, 0x0A, 0x01, 0x04}));
        assertThrows(OcspParseException.class, () -> OcspResponseParser.parseBase64("!!!not-base64!!!"));
    }
}
