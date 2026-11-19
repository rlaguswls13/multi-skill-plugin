package com.msp.crl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Pure Java 21 test utility to construct valid RFC 5280 X.509 CRL DER/PEM structures
 * using standard java.security without any 3rd party dependencies.
 */
public final class CrlTestUtil {

    private static final DateTimeFormatter UTC_TIME_FMT =
            DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    private CrlTestUtil() {}

    public record RevokedCertSpec(BigInteger serialNumber, Instant revocationDate, Integer reasonCode) {}

    public static byte[] generateDerCrl(
            KeyPair keyPair,
            String issuerCn,
            Instant thisUpdate,
            Instant nextUpdate,
            List<RevokedCertSpec> revokedList
    ) throws Exception {
        // Build TBSCertList
        ByteArrayOutputStream tbs = new ByteArrayOutputStream();

        // Version v2 (INTEGER 1) -> 02 01 01
        tbs.write(encodeInteger(BigInteger.ONE));

        // Signature Algorithm: SHA256withRSA
        tbs.write(encodeSha256WithRsaAlgorithmId());

        // Issuer: CN=<issuerCn>
        tbs.write(encodeIssuer(issuerCn));

        // ThisUpdate: UTCTime
        tbs.write(encodeUtcTime(thisUpdate));

        // NextUpdate: UTCTime (optional)
        if (nextUpdate != null) {
            tbs.write(encodeUtcTime(nextUpdate));
        }

        // Revoked Certificates SEQUENCE OF
        if (revokedList != null && !revokedList.isEmpty()) {
            ByteArrayOutputStream revokedSeq = new ByteArrayOutputStream();
            for (RevokedCertSpec spec : revokedList) {
                ByteArrayOutputStream entrySeq = new ByteArrayOutputStream();
                entrySeq.write(encodeInteger(spec.serialNumber()));
                entrySeq.write(encodeUtcTime(spec.revocationDate()));
                if (spec.reasonCode() != null) {
                    // crlEntryExtensions SEQUENCE { Extension { OID 2.5.29.21, OCTET STRING { ENUMERATED reason } } }
                    entrySeq.write(encodeReasonExtension(spec.reasonCode()));
                }
                revokedSeq.write(encodeSequence(entrySeq.toByteArray()));
            }
            tbs.write(encodeSequence(revokedSeq.toByteArray()));
        }

        byte[] tbsBytes = encodeSequence(tbs.toByteArray());

        // Sign TBS bytes with RSA PrivateKey
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(tbsBytes);
        byte[] signatureBytes = sig.sign();

        // Wrap into CertificateList SEQUENCE { tbs, algId, bitString(sig) }
        ByteArrayOutputStream crlSeq = new ByteArrayOutputStream();
        crlSeq.write(tbsBytes);
        crlSeq.write(encodeSha256WithRsaAlgorithmId());
        crlSeq.write(encodeBitString(signatureBytes));

        return encodeSequence(crlSeq.toByteArray());
    }

    public static String toPem(byte[] derCrl) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(derCrl);
        return "-----BEGIN X509 CRL-----\n" + b64 + "\n-----END X509 CRL-----\n";
    }

    private static byte[] encodeSha256WithRsaAlgorithmId() throws IOException {
        // SEQUENCE { OID 1.2.840.113549.1.1.11, NULL }
        byte[] oid = new byte[]{0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B};
        byte[] nullParam = new byte[]{0x05, 0x00};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(oid);
        out.write(nullParam);
        return encodeSequence(out.toByteArray());
    }

    private static byte[] encodeIssuer(String cn) throws IOException {
        // RDNSequence: SEQUENCE { SET { SEQUENCE { OID 2.5.4.3 (CN), UTF8String(cn) } } }
        byte[] cnOid = new byte[]{0x06, 0x03, 0x55, 0x04, 0x03};
        byte[] cnBytes = cn.getBytes(StandardCharsets.UTF_8);
        byte[] utf8String = encodeTl(0x0C, cnBytes);

        ByteArrayOutputStream attrSeq = new ByteArrayOutputStream();
        attrSeq.write(cnOid);
        attrSeq.write(utf8String);
        byte[] setContent = encodeSequence(attrSeq.toByteArray());

        byte[] rdnSet = encodeTl(0x31, setContent); // SET
        return encodeSequence(rdnSet);
    }

    private static byte[] encodeUtcTime(Instant instant) throws IOException {
        String str = UTC_TIME_FMT.format(instant);
        return encodeTl(0x17, str.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] encodeReasonExtension(int reasonCode) throws IOException {
        // Extensions ::= SEQUENCE { Extension }
        // Extension ::= SEQUENCE { extnID (2.5.29.21), extnValue OCTET STRING { ENUMERATED (0x0A, 0x01, reasonCode) } }
        byte[] reasonOid = new byte[]{0x06, 0x03, 0x55, 0x1D, 0x15};
        byte[] enumerated = encodeTl(0x0A, new byte[]{(byte) reasonCode});
        byte[] octetString = encodeTl(0x04, enumerated);

        ByteArrayOutputStream ext = new ByteArrayOutputStream();
        ext.write(reasonOid);
        ext.write(octetString);
        byte[] extSeq = encodeSequence(ext.toByteArray());
        return encodeSequence(extSeq);
    }

    private static byte[] encodeInteger(BigInteger val) throws IOException {
        return encodeTl(0x02, val.toByteArray());
    }

    private static byte[] encodeBitString(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x00); // 0 unused bits
        out.write(data);
        return encodeTl(0x03, out.toByteArray());
    }

    private static byte[] encodeSequence(byte[] content) throws IOException {
        return encodeTl(0x30, content);
    }

    private static byte[] encodeTl(int tag, byte[] val) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int len = val.length;
        if (len < 128) {
            out.write(len);
        } else if (len < 256) {
            out.write(0x81);
            out.write(len);
        } else if (len < 65536) {
            out.write(0x82);
            out.write((len >>> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x83);
            out.write((len >>> 16) & 0xFF);
            out.write((len >>> 8) & 0xFF);
            out.write(len & 0xFF);
        }
        out.write(val);
        return out.toByteArray();
    }
}
