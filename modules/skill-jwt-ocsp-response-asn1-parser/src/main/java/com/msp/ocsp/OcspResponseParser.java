package com.msp.ocsp;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Java 21 RFC 6960 X.509 OCSP Response ASN.1 DER parser.
 */
public final class OcspResponseParser {

    private OcspResponseParser() {
    }

    /**
     * Parses a Base64-encoded DER {@code OCSPResponse}.
     */
    public static OcspResponse parseBase64(String base64Der) {
        Objects.requireNonNull(base64Der, "base64Der must not be null");
        try {
            byte[] der = Base64.getMimeDecoder().decode(base64Der.trim());
            return parse(der);
        } catch (IllegalArgumentException e) {
            throw new OcspParseException("Invalid Base64 OCSP response", e);
        }
    }

    /**
     * Parses a DER-encoded RFC 6960 {@code OCSPResponse}.
     */
    public static OcspResponse parse(byte[] derBytes) {
        Objects.requireNonNull(derBytes, "derBytes must not be null");
        if (derBytes.length == 0) {
            throw new OcspParseException("Empty OCSP response");
        }

        DerCodec.Reader outer = new DerCodec.Reader(derBytes);
        DerCodec.Tlv ocspSeq = outer.readExpected(DerCodec.TAG_SEQUENCE, "OCSPResponse");
        outer.ensureExhausted("OCSPResponse");

        DerCodec.Reader seq = new DerCodec.Reader(ocspSeq.content());
        DerCodec.Tlv statusTlv = seq.readExpected(DerCodec.TAG_ENUMERATED, "responseStatus");
        OcspResponseStatus status = OcspResponseStatus.fromCode(DerCodec.decodeEnumerated(statusTlv.content()));

        if (!seq.hasRemaining()) {
            return new OcspResponse(status, Optional.empty(), Optional.empty());
        }

        DerCodec.Tlv respBytesWrapper = seq.readExpected(DerCodec.TAG_CTX_CONSTRUCTED_0, "responseBytes [0]");
        seq.ensureExhausted("OCSPResponse");

        DerCodec.Reader rbOuter = new DerCodec.Reader(respBytesWrapper.content());
        DerCodec.Tlv rbSeq = rbOuter.readExpected(DerCodec.TAG_SEQUENCE, "ResponseBytes");
        rbOuter.ensureExhausted("responseBytes [0]");

        DerCodec.Reader rbReader = new DerCodec.Reader(rbSeq.content());
        DerCodec.Tlv oidTlv = rbReader.readExpected(DerCodec.TAG_OID, "responseType");
        String responseTypeOid = DerCodec.decodeOid(oidTlv.content());
        DerCodec.Tlv responseOctets = rbReader.readExpected(DerCodec.TAG_OCTET_STRING, "response");
        rbReader.ensureExhausted("ResponseBytes");

        Optional<BasicOcspResponse> basic = Optional.empty();
        if (OcspResponse.ID_PKIX_OCSP_BASIC.equals(responseTypeOid)) {
            basic = Optional.of(parseBasicOcspResponse(responseOctets.content()));
        }

        return new OcspResponse(status, Optional.of(responseTypeOid), basic);
    }

    private static BasicOcspResponse parseBasicOcspResponse(byte[] bytes) {
        DerCodec.Reader outer = new DerCodec.Reader(bytes);
        DerCodec.Tlv basicSeq = outer.readExpected(DerCodec.TAG_SEQUENCE, "BasicOCSPResponse");
        outer.ensureExhausted("BasicOCSPResponse");

        byte[] seqContent = basicSeq.content();
        DerCodec.Reader reader = new DerCodec.Reader(seqContent);

        DerCodec.Tlv tbsTlv = reader.readExpected(DerCodec.TAG_SEQUENCE, "tbsResponseData");
        byte[] rawTbs = tbsTlv.rawSlice(seqContent);
        ResponseData responseData = parseResponseData(tbsTlv.content(), rawTbs);

        DerCodec.Tlv sigAlgTlv = reader.readExpected(DerCodec.TAG_SEQUENCE, "signatureAlgorithm");
        String sigAlgOid = parseAlgorithmOid(sigAlgTlv.content());

        DerCodec.Tlv sigBitString = reader.readExpected(DerCodec.TAG_BIT_STRING, "signature");
        byte[] signature = DerCodec.decodeBitString(sigBitString.content());

        List<byte[]> certs = new ArrayList<>();
        if (reader.hasRemaining()) {
            DerCodec.Tlv certsCtx = reader.readExpected(DerCodec.TAG_CTX_CONSTRUCTED_0, "certs [0]");
            DerCodec.Reader certsWrapper = new DerCodec.Reader(certsCtx.content());
            DerCodec.Tlv certsSeq = certsWrapper.readExpected(DerCodec.TAG_SEQUENCE, "SEQUENCE OF Certificate");
            certsWrapper.ensureExhausted("certs [0]");

            byte[] certsBytes = certsSeq.content();
            DerCodec.Reader certListReader = new DerCodec.Reader(certsBytes);
            while (certListReader.hasRemaining()) {
                DerCodec.Tlv certTlv = certListReader.readExpected(DerCodec.TAG_SEQUENCE, "Certificate");
                certs.add(certTlv.rawSlice(certsBytes));
            }
        }
        reader.ensureExhausted("BasicOCSPResponse");

        return new BasicOcspResponse(responseData, sigAlgOid, signature, certs);
    }

    private static ResponseData parseResponseData(byte[] content, byte[] rawTbs) {
        DerCodec.Reader reader = new DerCodec.Reader(content);

        // version [0] EXPLICIT Version DEFAULT v1 (0)
        int version = 0;
        if (reader.hasRemaining() && reader.peekTag() == DerCodec.TAG_CTX_CONSTRUCTED_0) {
            DerCodec.Tlv verCtx = reader.readTlv();
            DerCodec.Reader verReader = new DerCodec.Reader(verCtx.content());
            DerCodec.Tlv verInt = verReader.readExpected(DerCodec.TAG_INTEGER, "version");
            verReader.ensureExhausted("version [0]");
            version = DerCodec.decodeInteger(verInt.content()).intValueExact();
        }

        // responderID CHOICE { byName [1] Name, byKey [2] KeyHash }
        DerCodec.Tlv responderTlv = reader.readTlv();
        ResponderId responderId = switch (responderTlv.tag()) {
            case DerCodec.TAG_CTX_CONSTRUCTED_1 -> new ResponderId.ByName(responderTlv.content());
            case DerCodec.TAG_CTX_CONSTRUCTED_2 -> {
                DerCodec.Reader keyReader = new DerCodec.Reader(responderTlv.content());
                DerCodec.Tlv keyHashOctet = keyReader.readExpected(DerCodec.TAG_OCTET_STRING, "KeyHash");
                keyReader.ensureExhausted("responderID byKey");
                yield new ResponderId.ByKey(keyHashOctet.content());
            }
            default -> throw new OcspParseException(
                    "Invalid ResponderID tag: 0x" + Integer.toHexString(responderTlv.tag()));
        };

        // producedAt GeneralizedTime
        DerCodec.Tlv producedAtTlv = reader.readExpected(DerCodec.TAG_GENERALIZED_TIME, "producedAt");
        Instant producedAt = DerCodec.decodeGeneralizedTime(producedAtTlv.content());

        // responses SEQUENCE OF SingleResponse
        DerCodec.Tlv responsesSeq = reader.readExpected(DerCodec.TAG_SEQUENCE, "responses");
        List<SingleResponse> singleResponses = new ArrayList<>();
        DerCodec.Reader srReader = new DerCodec.Reader(responsesSeq.content());
        while (srReader.hasRemaining()) {
            DerCodec.Tlv srTlv = srReader.readExpected(DerCodec.TAG_SEQUENCE, "SingleResponse");
            singleResponses.add(parseSingleResponse(srTlv.content()));
        }

        // responseExtensions [1] EXPLICIT Extensions OPTIONAL
        boolean hasExtensions = false;
        if (reader.hasRemaining() && reader.peekTag() == DerCodec.TAG_CTX_CONSTRUCTED_1) {
            reader.readTlv();
            hasExtensions = true;
        }
        reader.ensureExhausted("ResponseData");

        return new ResponseData(version, responderId, producedAt, singleResponses, hasExtensions, rawTbs);
    }

    private static SingleResponse parseSingleResponse(byte[] content) {
        DerCodec.Reader reader = new DerCodec.Reader(content);

        // certID CertID
        DerCodec.Tlv certIdTlv = reader.readExpected(DerCodec.TAG_SEQUENCE, "CertID");
        CertId certId = parseCertId(certIdTlv.content());

        // certStatus CertStatus
        DerCodec.Tlv statusTlv = reader.readTlv();
        CertStatus certStatus = parseCertStatus(statusTlv);

        // thisUpdate GeneralizedTime
        DerCodec.Tlv thisUpdateTlv = reader.readExpected(DerCodec.TAG_GENERALIZED_TIME, "thisUpdate");
        Instant thisUpdate = DerCodec.decodeGeneralizedTime(thisUpdateTlv.content());

        // nextUpdate [0] EXPLICIT GeneralizedTime OPTIONAL
        Optional<Instant> nextUpdate = Optional.empty();
        if (reader.hasRemaining() && reader.peekTag() == DerCodec.TAG_CTX_CONSTRUCTED_0) {
            DerCodec.Tlv nextCtx = reader.readTlv();
            DerCodec.Reader nextReader = new DerCodec.Reader(nextCtx.content());
            DerCodec.Tlv nextTimeTlv = nextReader.readExpected(DerCodec.TAG_GENERALIZED_TIME, "nextUpdate");
            nextReader.ensureExhausted("nextUpdate [0]");
            nextUpdate = Optional.of(DerCodec.decodeGeneralizedTime(nextTimeTlv.content()));
        }

        // singleExtensions [1] EXPLICIT Extensions OPTIONAL
        boolean hasExtensions = false;
        if (reader.hasRemaining() && reader.peekTag() == DerCodec.TAG_CTX_CONSTRUCTED_1) {
            reader.readTlv();
            hasExtensions = true;
        }
        reader.ensureExhausted("SingleResponse");

        return new SingleResponse(certId, certStatus, thisUpdate, nextUpdate, hasExtensions);
    }

    private static CertId parseCertId(byte[] content) {
        DerCodec.Reader reader = new DerCodec.Reader(content);
        DerCodec.Tlv hashAlgTlv = reader.readExpected(DerCodec.TAG_SEQUENCE, "CertID.hashAlgorithm");
        String hashAlgOid = parseAlgorithmOid(hashAlgTlv.content());
        byte[] issuerNameHash = reader.readExpected(DerCodec.TAG_OCTET_STRING, "CertID.issuerNameHash").content();
        byte[] issuerKeyHash = reader.readExpected(DerCodec.TAG_OCTET_STRING, "CertID.issuerKeyHash").content();
        BigInteger serial = DerCodec.decodeInteger(
                reader.readExpected(DerCodec.TAG_INTEGER, "CertID.serialNumber").content());
        reader.ensureExhausted("CertID");
        return new CertId(hashAlgOid, issuerNameHash, issuerKeyHash, serial);
    }

    private static CertStatus parseCertStatus(DerCodec.Tlv statusTlv) {
        return switch (statusTlv.tag()) {
            case DerCodec.TAG_CTX_PRIMITIVE_0 -> {
                if (statusTlv.content().length != 0) {
                    throw new OcspParseException("CertStatus.good must have empty NULL content");
                }
                yield new CertStatus.Good();
            }
            case DerCodec.TAG_CTX_CONSTRUCTED_1 -> {
                DerCodec.Reader revReader = new DerCodec.Reader(statusTlv.content());
                DerCodec.Tlv revTimeTlv = revReader.readExpected(DerCodec.TAG_GENERALIZED_TIME, "revocationTime");
                Instant revTime = DerCodec.decodeGeneralizedTime(revTimeTlv.content());
                Optional<RevocationReason> reason = Optional.empty();
                if (revReader.hasRemaining() && revReader.peekTag() == DerCodec.TAG_CTX_CONSTRUCTED_0) {
                    DerCodec.Tlv reasonCtx = revReader.readTlv();
                    DerCodec.Reader reasonReader = new DerCodec.Reader(reasonCtx.content());
                    DerCodec.Tlv enumTlv = reasonReader.readExpected(DerCodec.TAG_ENUMERATED, "revocationReason");
                    reasonReader.ensureExhausted("revocationReason [0]");
                    reason = Optional.of(RevocationReason.fromCode(DerCodec.decodeEnumerated(enumTlv.content())));
                }
                revReader.ensureExhausted("RevokedInfo");
                yield new CertStatus.Revoked(revTime, reason);
            }
            case DerCodec.TAG_CTX_PRIMITIVE_2 -> {
                if (statusTlv.content().length != 0) {
                    throw new OcspParseException("CertStatus.unknown must have empty NULL content");
                }
                yield new CertStatus.Unknown();
            }
            default -> throw new OcspParseException(
                    "Invalid CertStatus tag: 0x" + Integer.toHexString(statusTlv.tag()));
        };
    }

    private static String parseAlgorithmOid(byte[] algIdContent) {
        DerCodec.Reader reader = new DerCodec.Reader(algIdContent);
        DerCodec.Tlv oidTlv = reader.readExpected(DerCodec.TAG_OID, "AlgorithmIdentifier.algorithm");
        // optional parameters are allowed and ignored here
        return DerCodec.decodeOid(oidTlv.content());
    }
}
