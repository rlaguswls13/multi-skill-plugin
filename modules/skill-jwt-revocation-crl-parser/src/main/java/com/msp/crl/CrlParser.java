package com.msp.crl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance RFC 5280 X.509 Certificate Revocation List (CRL) parser.
 * Supports binary DER and PEM formatted CRL data.
 */
public final class CrlParser {

    private static final String CRL_NUMBER_OID = "2.5.29.20";
    private static final Pattern PEM_PATTERN = Pattern.compile(
            "-----BEGIN (?:X509 )?CRL-----\\s*([A-Za-z0-9+/=\\r\\n]+)\\s*-----END (?:X509 )?CRL-----"
    );

    private CrlParser() {}

    /**
     * Parses CRL from raw bytes (auto-detects PEM or DER).
     */
    public static CrlSnapshot parse(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length == 0) {
            throw new CrlParseException("Empty CRL data");
        }

        // Check if data is PEM formatted
        String text = new String(data, StandardCharsets.US_ASCII);
        if (text.contains("-----BEGIN")) {
            return parsePem(text);
        }

        return parseDer(data);
    }

    /**
     * Parses CRL from PEM encoded string.
     */
    public static CrlSnapshot parsePem(String pemString) {
        Objects.requireNonNull(pemString, "pemString must not be null");
        Matcher matcher = PEM_PATTERN.matcher(pemString);
        if (!matcher.find()) {
            throw new CrlParseException("No valid PEM CRL header/footer found");
        }

        String base64 = matcher.group(1).replaceAll("\\s+", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new CrlParseException("Invalid Base64 encoding in PEM CRL", e);
        }
        return parseDer(der);
    }

    /**
     * Parses CRL from InputStream.
     */
    public static CrlSnapshot parse(InputStream in) {
        Objects.requireNonNull(in, "in must not be null");
        try {
            byte[] allBytes = in.readAllBytes();
            return parse(allBytes);
        } catch (Exception e) {
            throw new CrlParseException("Failed to read CRL from stream", e);
        }
    }

    /**
     * Converts a standard Java X509CRL into a CrlSnapshot.
     */
    public static CrlSnapshot fromX509CRL(X509CRL crl) {
        Objects.requireNonNull(crl, "crl must not be null");
        try {
            String issuerDN = crl.getIssuerX500Principal().getName();
            Instant thisUpdate = crl.getThisUpdate().toInstant();
            Instant nextUpdate = crl.getNextUpdate() != null ? crl.getNextUpdate().toInstant() : null;
            String sigAlgName = crl.getSigAlgName();
            String sigAlgOID = crl.getSigAlgOID();
            BigInteger crlNumber = parseCrlNumber(crl.getExtensionValue(CRL_NUMBER_OID));

            Map<BigInteger, RevocationInfo> revokedMap = new HashMap<>();
            Set<? extends X509CRLEntry> entries = crl.getRevokedCertificates();
            if (entries != null) {
                for (X509CRLEntry entry : entries) {
                    BigInteger serial = entry.getSerialNumber();
                    Instant revDate = entry.getRevocationDate().toInstant();
                    CRLReason reason = entry.getRevocationReason();
                    revokedMap.put(serial, RevocationInfo.of(serial, revDate, reason));
                }
            }

            return new CrlSnapshot(
                    issuerDN,
                    crl.getIssuerX500Principal(),
                    thisUpdate,
                    nextUpdate,
                    sigAlgName,
                    sigAlgOID,
                    crlNumber,
                    revokedMap
            );
        } catch (Exception e) {
            throw new CrlParseException("Failed to parse X509CRL", e);
        }
    }

    private static CrlSnapshot parseDer(byte[] derBytes) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(derBytes));
            if (crl == null) {
                throw new CrlParseException("CertificateFactory returned null for CRL");
            }
            return fromX509CRL(crl);
        } catch (CRLException | CertificateException e) {
            throw new CrlParseException("Failed to generate X509CRL from DER bytes: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts BigInteger CRL number from DER encoded OctetString wrapping ASN.1 INTEGER.
     */
    private static BigInteger parseCrlNumber(byte[] extValue) {
        if (extValue == null || extValue.length < 3) {
            return null;
        }
        try {
            // Extension value is OCTET STRING: 04 <len> [ASN.1 Integer: 02 <len> <bytes>]
            int idx = 0;
            if (extValue[idx++] != 0x04) return null; // Tag OCTET STRING
            int octetLen = extValue[idx++] & 0xFF;
            if ((octetLen & 0x80) != 0) {
                int count = octetLen & 0x7F;
                idx += count;
            }
            if (idx >= extValue.length || extValue[idx++] != 0x02) return null; // Tag INTEGER
            int intLen = extValue[idx++] & 0xFF;
            if ((intLen & 0x80) != 0) {
                int count = intLen & 0x7F;
                intLen = 0;
                for (int i = 0; i < count; i++) {
                    intLen = (intLen << 8) | (extValue[idx++] & 0xFF);
                }
            }
            byte[] intBytes = Arrays.copyOfRange(extValue, idx, idx + intLen);
            return new BigInteger(intBytes);
        } catch (Exception e) {
            return null;
        }
    }
}
