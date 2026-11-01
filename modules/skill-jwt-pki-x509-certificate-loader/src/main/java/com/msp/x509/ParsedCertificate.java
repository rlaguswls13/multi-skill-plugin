package com.msp.x509;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parsed details of an X.509 certificate for JWT, TLS, and PKI applications.
 */
public record ParsedCertificate(
        X509Certificate certificate,
        String subjectDn,
        String issuerDn,
        BigInteger serialNumber,
        String serialNumberHex,
        Instant notBefore,
        Instant notAfter,
        String sigAlgName,
        String sigAlgOid,
        PublicKey publicKey,
        String keyAlgorithm,
        Integer keySizeBits,
        KeyUsageFlags keyUsage,
        List<String> extendedKeyUsage,
        SubjectAltNames subjectAltNames,
        boolean isSelfSigned,
        boolean isCa
) {
    public ParsedCertificate {
        Objects.requireNonNull(certificate, "certificate cannot be null");
        extendedKeyUsage = extendedKeyUsage != null ? List.copyOf(extendedKeyUsage) : Collections.emptyList();
        subjectAltNames = subjectAltNames != null ? subjectAltNames : SubjectAltNames.EMPTY;
        keyUsage = keyUsage != null ? keyUsage : KeyUsageFlags.NONE;
    }

    public boolean isExpired(Instant at) {
        Instant check = at != null ? at : Instant.now();
        return check.isAfter(notAfter);
    }

    public boolean isValidAt(Instant at) {
        Instant check = at != null ? at : Instant.now();
        return !check.isBefore(notBefore) && !check.isAfter(notAfter);
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public String getSubjectDn() {
        return subjectDn;
    }

    public String getIssuerDn() {
        return issuerDn;
    }

    public BigInteger getSerialNumber() {
        return serialNumber;
    }

    public String getSerialNumberHex() {
        return serialNumberHex;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public Instant getNotAfter() {
        return notAfter;
    }

    public String getSigAlgName() {
        return sigAlgName;
    }

    public String getSigAlgOid() {
        return sigAlgOid;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public Integer getKeySizeBits() {
        return keySizeBits;
    }

    public KeyUsageFlags getKeyUsage() {
        return keyUsage;
    }

    public List<String> getExtendedKeyUsage() {
        return extendedKeyUsage;
    }

    public SubjectAltNames getSubjectAltNames() {
        return subjectAltNames;
    }
}
