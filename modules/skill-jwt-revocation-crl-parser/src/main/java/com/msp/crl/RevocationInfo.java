package com.msp.crl;

import java.math.BigInteger;
import java.security.cert.CRLReason;
import java.time.Instant;
import java.util.Objects;

/**
 * Details of a revoked certificate within an RFC 5280 CRL.
 *
 * @param serialNumber      the certificate serial number as BigInteger
 * @param serialNumberHex   the certificate serial number formatted as uppercase hexadecimal
 * @param revocationDate    the timestamp when the revocation took effect
 * @param reason            the RFC 5280 CRLReason code (null if unspecified)
 * @param reasonDescription human-readable reason description
 */
public record RevocationInfo(
        BigInteger serialNumber,
        String serialNumberHex,
        Instant revocationDate,
        CRLReason reason,
        String reasonDescription
) {

    public RevocationInfo {
        Objects.requireNonNull(serialNumber, "serialNumber must not be null");
        Objects.requireNonNull(serialNumberHex, "serialNumberHex must not be null");
        Objects.requireNonNull(revocationDate, "revocationDate must not be null");
    }

    public static RevocationInfo of(BigInteger serialNumber, Instant revocationDate, CRLReason reason) {
        String hex = formatHex(serialNumber);
        String desc = reason != null ? reason.name() : "UNSPECIFIED";
        return new RevocationInfo(serialNumber, hex, revocationDate, reason, desc);
    }

    public static String formatHex(BigInteger serialNumber) {
        String hex = serialNumber.toString(16).toUpperCase();
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }
        return hex;
    }
}
