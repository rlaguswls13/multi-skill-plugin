package com.msp.x509;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Pure Java 21 X.509 Certificate and Certificate Chain Loader & Parser for JWT and TLS/PKI.
 *
 * <p>Supports PEM and DER formats, SAN extraction, KeyUsage inspection,
 * chain verification, and in-memory self-signed test certificate generation.</p>
 */
public final class X509CertificateLoader {

    private static final CertificateFactory CERT_FACTORY;

    static {
        try {
            CERT_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private X509CertificateLoader() {
    }

    /**
     * Loads a single X.509 certificate from a PEM-encoded string.
     */
    public static X509Certificate loadFromPem(String pem) throws CertificateException {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM string cannot be null or blank");
        }
        InputStream is = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
        return (X509Certificate) CERT_FACTORY.generateCertificate(is);
    }

    /**
     * Loads a certificate chain (one or more certificates) from PEM-encoded string.
     */
    @SuppressWarnings("unchecked")
    public static List<X509Certificate> loadChainFromPem(String pemChain) throws CertificateException {
        if (pemChain == null || pemChain.isBlank()) {
            throw new IllegalArgumentException("PEM chain cannot be null or blank");
        }
        InputStream is = new ByteArrayInputStream(pemChain.getBytes(StandardCharsets.UTF_8));
        Collection<? extends Certificate> certs = CERT_FACTORY.generateCertificates(is);
        List<X509Certificate> list = new ArrayList<>();
        for (Certificate cert : certs) {
            if (cert instanceof X509Certificate x509) {
                list.add(x509);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Loads an X.509 certificate from raw binary DER bytes.
     */
    public static X509Certificate loadFromDer(byte[] derBytes) throws CertificateException {
        if (derBytes == null || derBytes.length == 0) {
            throw new IllegalArgumentException("DER bytes cannot be null or empty");
        }
        return (X509Certificate) CERT_FACTORY.generateCertificate(new ByteArrayInputStream(derBytes));
    }

    /**
     * Parses an {@link X509Certificate} into a comprehensive {@link ParsedCertificate}.
     */
    public static ParsedCertificate parse(X509Certificate cert) {
        Objects.requireNonNull(cert, "cert cannot be null");

        String subjectDn = cert.getSubjectX500Principal().getName();
        String issuerDn = cert.getIssuerX500Principal().getName();
        BigInteger serialNumber = cert.getSerialNumber();
        String serialNumberHex = serialNumber.toString(16).toUpperCase(Locale.ROOT);
        Instant notBefore = cert.getNotBefore().toInstant();
        Instant notAfter = cert.getNotAfter().toInstant();
        String sigAlgName = cert.getSigAlgName();
        String sigAlgOid = cert.getSigAlgOID();

        PublicKey publicKey = cert.getPublicKey();
        String keyAlgorithm = publicKey != null ? publicKey.getAlgorithm() : null;
        Integer keySizeBits = extractKeySize(publicKey);

        KeyUsageFlags keyUsage = KeyUsageFlags.fromArray(cert.getKeyUsage());
        List<String> extendedKeyUsage = extractExtendedKeyUsage(cert);
        SubjectAltNames san = extractSubjectAltNames(cert);

        boolean isSelfSigned = isSelfSignedCertificate(cert);
        boolean isCa = cert.getBasicConstraints() != -1;

        return new ParsedCertificate(
                cert,
                subjectDn,
                issuerDn,
                serialNumber,
                serialNumberHex,
                notBefore,
                notAfter,
                sigAlgName,
                sigAlgOid,
                publicKey,
                keyAlgorithm,
                keySizeBits,
                keyUsage,
                extendedKeyUsage,
                san,
                isSelfSigned,
                isCa
        );
    }

    /**
     * Parses PEM string directly into a {@link ParsedCertificate}.
     */
    public static ParsedCertificate parsePem(String pem) throws CertificateException {
        return parse(loadFromPem(pem));
    }

    /**
     * Parses PEM chain string into a list of {@link ParsedCertificate}.
     */
    public static List<ParsedCertificate> parseChainPem(String pemChain) throws CertificateException {
        List<X509Certificate> certs = loadChainFromPem(pemChain);
        List<ParsedCertificate> list = new ArrayList<>(certs.size());
        for (X509Certificate c : certs) {
            list.add(parse(c));
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Verifies a certificate chain from leaf (index 0) up to intermediate/root certificates.
     *
     * @param chain list of certificates with leaf at index 0
     * @param trustAnchorKey public key of trust anchor (root CA), or null if self-contained
     * @param verificationTime timestamp to check validity (or null for current time)
     * @return ValidationResult
     */
    public static ValidationResult verifyChain(
            List<X509Certificate> chain,
            PublicKey trustAnchorKey,
            Instant verificationTime
    ) {
        if (chain == null || chain.isEmpty()) {
            return ValidationResult.failure("Certificate chain is empty", -1);
        }

        Date checkDate = Date.from(verificationTime != null ? verificationTime : Instant.now());

        for (int i = 0; i < chain.size(); i++) {
            X509Certificate current = chain.get(i);
            try {
                current.checkValidity(checkDate);
            } catch (CertificateExpiredException e) {
                return ValidationResult.failure("Certificate at index " + i + " is expired at " + checkDate, i);
            } catch (CertificateNotYetValidException e) {
                return ValidationResult.failure("Certificate at index " + i + " is not yet valid at " + checkDate, i);
            }

            if (i < chain.size() - 1) {
                X509Certificate next = chain.get(i + 1);
                // Verify current cert is signed by next cert's public key
                try {
                    current.verify(next.getPublicKey());
                } catch (Exception e) {
                    return ValidationResult.failure("Certificate at index " + i + " signature invalid: " + e.getMessage(), i);
                }
            } else {
                // Top certificate in chain
                if (trustAnchorKey != null) {
                    try {
                        current.verify(trustAnchorKey);
                    } catch (Exception e) {
                        return ValidationResult.failure("Root certificate at index " + i + " signature cannot be verified by trust anchor: " + e.getMessage(), i);
                    }
                }
            }
        }

        return ValidationResult.success();
    }

    /**
     * Encodes an X.509 certificate to PEM string format.
     */
    public static String toPem(X509Certificate cert) throws CertificateEncodingException {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(cert.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----\n";
    }

    /**
     * Encodes a list of certificates into a concatenated PEM chain string.
     */
    public static String toPemChain(List<X509Certificate> chain) throws CertificateEncodingException {
        StringBuilder sb = new StringBuilder();
        for (X509Certificate cert : chain) {
            sb.append(toPem(cert));
        }
        return sb.toString();
    }

    /**
     * Generates an in-memory self-signed X.509 certificate using pure standard Java.
     *
     * @param keyPair RSA KeyPair
     * @param dn Distinguished Name, e.g. "CN=Test Cert, O=MSP"
     * @param validityDays number of days valid
     * @return X509Certificate
     */
    public static X509Certificate createSelfSignedRsaCertificate(KeyPair keyPair, String dn, int validityDays)
            throws Exception {
        Instant notBefore = Instant.now().minusSeconds(60);
        Instant notAfter = notBefore.plusSeconds((long) validityDays * 86400);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        byte[] tbs = encodeTbsCertificate(serial, dn, notBefore, notAfter, keyPair.getPublic());
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(tbs);
        byte[] signatureBytes = sig.sign();

        byte[] certDer = encodeCertificate(tbs, signatureBytes);
        return loadFromDer(certDer);
    }

    private static Integer extractKeySize(PublicKey publicKey) {
        if (publicKey instanceof RSAPublicKey rsa) {
            return rsa.getModulus().bitLength();
        } else if (publicKey instanceof ECPublicKey ec) {
            return ec.getParams().getOrder().bitLength();
        }
        return null;
    }

    private static List<String> extractExtendedKeyUsage(X509Certificate cert) {
        try {
            List<String> eku = cert.getExtendedKeyUsage();
            return eku != null ? eku : Collections.emptyList();
        } catch (CertificateParsingException e) {
            return Collections.emptyList();
        }
    }

    private static SubjectAltNames extractSubjectAltNames(X509Certificate cert) {
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) {
                return SubjectAltNames.EMPTY;
            }
            List<String> dns = new ArrayList<>();
            List<String> ip = new ArrayList<>();
            List<String> uri = new ArrayList<>();
            List<String> email = new ArrayList<>();

            for (List<?> entry : sans) {
                if (entry.size() >= 2 && entry.get(0) instanceof Integer type && entry.get(1) instanceof String val) {
                    switch (type) {
                        case 1 -> email.add(val);
                        case 2 -> dns.add(val);
                        case 6 -> uri.add(val);
                        case 7 -> ip.add(val);
                        default -> {}
                    }
                }
            }
            return new SubjectAltNames(dns, ip, uri, email);
        } catch (CertificateParsingException e) {
            return SubjectAltNames.EMPTY;
        }
    }

    private static boolean isSelfSignedCertificate(X509Certificate cert) {
        if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
            return false;
        }
        try {
            cert.verify(cert.getPublicKey());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Minimal Pure-Java DER Encoding for Self-Signed RSA Certificates ---

    private static final byte[] SHA256_WITH_RSA_OID = new byte[]{
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b, 0x05, 0x00
    };

    private static byte[] encodeCertificate(byte[] tbs, byte[] signature) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tbs);
        out.write(SHA256_WITH_RSA_OID);
        // Bit String: 0 unused bits followed by signature bytes
        byte[] bitString = new byte[signature.length + 1];
        bitString[0] = 0x00;
        System.arraycopy(signature, 0, bitString, 1, signature.length);
        writeDerElement(out, 0x03, bitString);

        return wrapSequence(out.toByteArray());
    }

    private static byte[] encodeTbsCertificate(
            BigInteger serial,
            String dn,
            Instant notBefore,
            Instant notAfter,
            PublicKey publicKey
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Version 2 (v3) -> [0] EXPLICIT { INTEGER 2 }
        byte[] v3Int = new byte[]{0x02, 0x01, 0x02};
        writeDerElement(out, (byte) 0xa0, v3Int);

        // Serial Number
        byte[] serialBytes = serial.toByteArray();
        writeDerElement(out, 0x02, serialBytes);

        // Signature Algorithm (SHA256withRSA)
        out.write(SHA256_WITH_RSA_OID);

        // Issuer DN
        byte[] dnDer = encodeDn(dn);
        out.write(dnDer);

        // Validity (UTCTime format: YYMMDDHHMMSSZ)
        DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);
        byte[] nbBytes = utcFormatter.format(notBefore).getBytes(StandardCharsets.US_ASCII);
        byte[] naBytes = utcFormatter.format(notAfter).getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream valOut = new ByteArrayOutputStream();
        writeDerElement(valOut, 0x17, nbBytes);
        writeDerElement(valOut, 0x17, naBytes);
        writeDerElement(out, 0x30, valOut.toByteArray());

        // Subject DN
        out.write(dnDer);

        // SubjectPublicKeyInfo (already DER encoded by Java)
        out.write(publicKey.getEncoded());

        return wrapSequence(out.toByteArray());
    }

    private static byte[] encodeDn(String dn) throws IOException {
        // Parse simple CN=..., O=... attributes
        ByteArrayOutputStream rdnSeq = new ByteArrayOutputStream();
        String[] parts = dn.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().toUpperCase(Locale.ROOT);
                String val = kv[1].trim();
                byte[] oid = switch (key) {
                    case "CN" -> new byte[]{0x06, 0x03, 0x55, 0x04, 0x03}; // 2.5.4.3
                    case "O" -> new byte[]{0x06, 0x03, 0x55, 0x04, 0x0a};  // 2.5.4.10
                    case "OU" -> new byte[]{0x06, 0x03, 0x55, 0x04, 0x0b}; // 2.5.4.11
                    case "C" -> new byte[]{0x06, 0x03, 0x55, 0x04, 0x06};  // 2.5.4.6
                    default -> new byte[]{0x06, 0x03, 0x55, 0x04, 0x03};
                };
                ByteArrayOutputStream atv = new ByteArrayOutputStream();
                atv.write(oid);
                writeDerElement(atv, 0x0c, val.getBytes(StandardCharsets.UTF_8)); // UTF8String
                byte[] atvSeq = wrapSequence(atv.toByteArray());
                writeDerElement(rdnSeq, 0x31, atvSeq); // SET of AttributeTypeAndValue
            }
        }
        return wrapSequence(rdnSeq.toByteArray());
    }

    private static byte[] wrapSequence(byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeDerElement(out, 0x30, content);
        return out.toByteArray();
    }

    private static void writeDerElement(ByteArrayOutputStream out, int tag, byte[] val) throws IOException {
        out.write(tag);
        writeDerLength(out, val.length);
        out.write(val);
    }

    private static void writeDerLength(ByteArrayOutputStream out, int length) {
        if (length < 128) {
            out.write(length);
        } else if (length < 256) {
            out.write(0x81);
            out.write(length);
        } else if (length < 65536) {
            out.write(0x82);
            out.write((length >> 8) & 0xff);
            out.write(length & 0xff);
        } else {
            out.write(0x83);
            out.write((length >> 16) & 0xff);
            out.write((length >> 8) & 0xff);
            out.write(length & 0xff);
        }
    }
}
