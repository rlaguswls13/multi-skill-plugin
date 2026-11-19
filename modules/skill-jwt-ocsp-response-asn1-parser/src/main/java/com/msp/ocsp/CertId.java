package com.msp.ocsp;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * RFC 6960 §4.1.1 {@code CertID} identifying a certificate by issuer hashes and serial number.
 */
public record CertId(
        String hashAlgorithmOid,
        byte[] issuerNameHash,
        byte[] issuerKeyHash,
        BigInteger serialNumber
) {
    public CertId {
        Objects.requireNonNull(hashAlgorithmOid, "hashAlgorithmOid must not be null");
        Objects.requireNonNull(issuerNameHash, "issuerNameHash must not be null");
        Objects.requireNonNull(issuerKeyHash, "issuerKeyHash must not be null");
        Objects.requireNonNull(serialNumber, "serialNumber must not be null");
        issuerNameHash = issuerNameHash.clone();
        issuerKeyHash = issuerKeyHash.clone();
    }

    @Override
    public byte[] issuerNameHash() {
        return issuerNameHash.clone();
    }

    @Override
    public byte[] issuerKeyHash() {
        return issuerKeyHash.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CertId other)) return false;
        return hashAlgorithmOid.equals(other.hashAlgorithmOid)
                && Arrays.equals(issuerNameHash, other.issuerNameHash)
                && Arrays.equals(issuerKeyHash, other.issuerKeyHash)
                && serialNumber.equals(other.serialNumber);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(hashAlgorithmOid, serialNumber);
        result = 31 * result + Arrays.hashCode(issuerNameHash);
        result = 31 * result + Arrays.hashCode(issuerKeyHash);
        return result;
    }
}
