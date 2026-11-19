package com.msp.ocsp;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent ASN.1 DER builder for RFC 6960 {@code OCSPResponse} structures.
 */
public final class OcspResponseBuilder {

    public static final String OID_SHA1 = "1.3.14.3.2.26";
    public static final String OID_SHA256 = "2.16.840.1.101.3.4.2.1";
    public static final String OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11";

    private OcspResponseStatus status = OcspResponseStatus.SUCCESSFUL;
    private int version = 0;
    private boolean explicitVersion;
    private ResponderId responderId;
    private Instant producedAt = Instant.now();
    private final List<SingleResponse> singleResponses = new ArrayList<>();
    private String signatureAlgorithmOid = OID_SHA256_WITH_RSA;
    private byte[] signature = new byte[]{0x01, 0x02, 0x03, 0x04};
    private final List<byte[]> certificates = new ArrayList<>();

    private OcspResponseBuilder() {
    }

    public static OcspResponseBuilder builder() {
        return new OcspResponseBuilder();
    }

    /**
     * Builds a status-only DER {@code OCSPResponse} (e.g., {@code TRY_LATER}, {@code UNAUTHORIZED}).
     */
    public static byte[] buildStatusOnly(OcspResponseStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return DerCodec.encodeSequence(DerCodec.encodeEnumerated(status.code()));
    }

    public OcspResponseBuilder status(OcspResponseStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        return this;
    }

    public OcspResponseBuilder version(int version) {
        this.version = version;
        this.explicitVersion = true;
        return this;
    }

    public OcspResponseBuilder responderId(ResponderId responderId) {
        this.responderId = Objects.requireNonNull(responderId, "responderId must not be null");
        return this;
    }

    public OcspResponseBuilder producedAt(Instant producedAt) {
        this.producedAt = Objects.requireNonNull(producedAt, "producedAt must not be null");
        return this;
    }

    public OcspResponseBuilder addSingleResponse(SingleResponse response) {
        this.singleResponses.add(Objects.requireNonNull(response, "response must not be null"));
        return this;
    }

    public OcspResponseBuilder signatureAlgorithmOid(String oid) {
        this.signatureAlgorithmOid = Objects.requireNonNull(oid, "oid must not be null");
        return this;
    }

    public OcspResponseBuilder signature(byte[] signature) {
        this.signature = Objects.requireNonNull(signature, "signature must not be null").clone();
        return this;
    }

    public OcspResponseBuilder addCertificateDer(byte[] certDer) {
        this.certificates.add(Objects.requireNonNull(certDer, "certDer must not be null").clone());
        return this;
    }

    /**
     * Encodes the configured OCSP response into ASN.1 DER bytes.
     */
    public byte[] buildDer() {
        if (status != OcspResponseStatus.SUCCESSFUL && responderId == null && singleResponses.isEmpty()) {
            return buildStatusOnly(status);
        }
        if (responderId == null) {
            throw new IllegalStateException("responderId is required for BasicOCSPResponse");
        }

        byte[] versionField = explicitVersion
                ? DerCodec.wrap(DerCodec.TAG_CTX_CONSTRUCTED_0, DerCodec.encodeInteger(BigInteger.valueOf(version)))
                : null;

        byte[] responderField = switch (responderId) {
            case ResponderId.ByName byName ->
                    DerCodec.wrap(DerCodec.TAG_CTX_CONSTRUCTED_1, byName.encodedName());
            case ResponderId.ByKey byKey ->
                    DerCodec.wrap(DerCodec.TAG_CTX_CONSTRUCTED_2, DerCodec.encodeOctetString(byKey.keyHash()));
        };

        byte[] producedAtField = DerCodec.encodeGeneralizedTime(producedAt);

        byte[][] encodedSingles = new byte[singleResponses.size()][];
        for (int i = 0; i < singleResponses.size(); i++) {
            encodedSingles[i] = encodeSingleResponse(singleResponses.get(i));
        }
        byte[] responsesSeq = DerCodec.encodeSequence(encodedSingles);

        byte[] tbsResponseData = DerCodec.encodeSequence(
                versionField,
                responderField,
                producedAtField,
                responsesSeq
        );

        byte[] sigAlg = DerCodec.encodeAlgorithmIdentifier(signatureAlgorithmOid);
        byte[] sigBits = DerCodec.encodeBitString(signature);

        byte[] certsField = null;
        if (!certificates.isEmpty()) {
            byte[] certSeq = DerCodec.encodeSequence(certificates.toArray(byte[][]::new));
            certsField = DerCodec.wrap(DerCodec.TAG_CTX_CONSTRUCTED_0, certSeq);
        }

        byte[] basicOcsp = DerCodec.encodeSequence(tbsResponseData, sigAlg, sigBits, certsField);
        byte[] responseBytes = DerCodec.encodeSequence(
                DerCodec.encodeOid(OcspResponse.ID_PKIX_OCSP_BASIC),
                DerCodec.encodeOctetString(basicOcsp)
        );

        return DerCodec.encodeSequence(
                DerCodec.encodeEnumerated(status.code()),
                DerCodec.wrap(DerCodec.TAG_CTX_CONSTRUCTED_0, responseBytes)
        );
    }

    private static byte[] encodeSingleResponse(SingleResponse sr) {
        CertId cid = sr.certId();
        byte[] certIdDer = DerCodec.encodeSequence(
                DerCodec.encodeAlgorithmIdentifier(cid.hashAlgorithmOid()),
                DerCodec.encodeOctetString(cid.issuerNameHash()),
                DerCodec.encodeOctetString(cid.issuerKeyHash()),
                DerCodec.encodeInteger(cid.serialNumber())
        );

        byte[] certStatusDer = switch (sr.certStatus()) {
            case CertStatus.Good good -> new byte[]{(byte) DerCodec.TAG_CTX_PRIMITIVE_0, 0x00};
            case CertStatus.Unknown unknown -> new byte[]{(byte) DerCodec.TAG_CTX_PRIMITIVE_2, 0x00};
            case CertStatus.Revoked rev -> {
                byte[] revTime = DerCodec.encodeGeneralizedTime(rev.revocationTime());
                byte[] reasonDer = rev.reason()
                        .map(r -> DerCodec.wrap(DerCodec.TAG_CTX_CONSTRUCTED_0, DerCodec.encodeEnumerated(r.code())))
                        .orElse(null);
                yield DerCodec.wrap(DerCodec.TAG_CTX_CONSTRUCTED_1, DerCodec.concat(revTime, reasonDer));
            }
        };

        byte[] thisUpdateDer = DerCodec.encodeGeneralizedTime(sr.thisUpdate());
        byte[] nextUpdateDer = sr.nextUpdate()
                .map(nu -> DerCodec.wrap(DerCodec.TAG_CTX_CONSTRUCTED_0, DerCodec.encodeGeneralizedTime(nu)))
                .orElse(null);

        return DerCodec.encodeSequence(certIdDer, certStatusDer, thisUpdateDer, nextUpdateDer);
    }
}
