package com.msp.crl;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

/**
 * Immutable snapshot of parsed RFC 5280 X.509 Certificate Revocation List (CRL).
 * Provides fast O(1) in-memory revocation lookups and expiry checks.
 */
public final class CrlSnapshot {

    private final String issuerDN;
    private final X500Principal issuerPrincipal;
    private final Instant thisUpdate;
    private final Instant nextUpdate;
    private final String signatureAlgorithm;
    private final String sigAlgOID;
    private final BigInteger crlNumber;
    private final Map<BigInteger, RevocationInfo> revokedMap;
    private final Map<String, RevocationInfo> hexMap;

    public CrlSnapshot(
            String issuerDN,
            X500Principal issuerPrincipal,
            Instant thisUpdate,
            Instant nextUpdate,
            String signatureAlgorithm,
            String sigAlgOID,
            BigInteger crlNumber,
            Map<BigInteger, RevocationInfo> revokedCertificates
    ) {
        this.issuerDN = Objects.requireNonNull(issuerDN, "issuerDN must not be null");
        this.issuerPrincipal = issuerPrincipal;
        this.thisUpdate = Objects.requireNonNull(thisUpdate, "thisUpdate must not be null");
        this.nextUpdate = nextUpdate;
        this.signatureAlgorithm = signatureAlgorithm != null ? signatureAlgorithm : "UNKNOWN";
        this.sigAlgOID = sigAlgOID;
        this.crlNumber = crlNumber;

        Map<BigInteger, RevocationInfo> rMap = new HashMap<>(revokedCertificates);
        Map<String, RevocationInfo> hMap = new HashMap<>();
        for (RevocationInfo info : rMap.values()) {
            hMap.put(normalizeHex(info.serialNumberHex()), info);
        }
        this.revokedMap = Collections.unmodifiableMap(rMap);
        this.hexMap = Collections.unmodifiableMap(hMap);
    }

    public String getIssuerDN() {
        return issuerDN;
    }

    public X500Principal getIssuerPrincipal() {
        return issuerPrincipal;
    }

    public Instant getThisUpdate() {
        return thisUpdate;
    }

    public Instant getNextUpdate() {
        return nextUpdate;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public String getSigAlgOID() {
        return sigAlgOID;
    }

    public Optional<BigInteger> getCrlNumber() {
        return Optional.ofNullable(crlNumber);
    }

    public int revokedCount() {
        return revokedMap.size();
    }

    public Map<BigInteger, RevocationInfo> getRevokedCertificates() {
        return revokedMap;
    }

    /**
     * Checks if the CRL is expired relative to current wall-clock time.
     */
    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    /**
     * Checks if the CRL is expired relative to a given point in time.
     */
    public boolean isExpired(Instant at) {
        Objects.requireNonNull(at, "at must not be null");
        return nextUpdate != null && at.isAfter(nextUpdate);
    }

    /**
     * Checks if a certificate serial number is revoked.
     */
    public boolean isRevoked(BigInteger serialNumber) {
        if (serialNumber == null) return false;
        return revokedMap.containsKey(serialNumber);
    }

    /**
     * Checks if a certificate serial number (in hex or decimal format) is revoked.
     */
    public boolean isRevoked(String serialNumber) {
        return getRevocationInfo(serialNumber).isPresent();
    }

    /**
     * Retrieves revocation metadata for a given serial number if revoked.
     */
    public Optional<RevocationInfo> getRevocationInfo(BigInteger serialNumber) {
        if (serialNumber == null) return Optional.empty();
        return Optional.ofNullable(revokedMap.get(serialNumber));
    }

    /**
     * Retrieves revocation metadata for a given hex or decimal serial number if revoked.
     */
    public Optional<RevocationInfo> getRevocationInfo(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) return Optional.empty();
        RevocationInfo byHex = hexMap.get(normalizeHex(serialNumber));
        if (byHex != null) {
            return Optional.of(byHex);
        }
        try {
            BigInteger dec = new BigInteger(serialNumber.trim(), 10);
            return Optional.ofNullable(revokedMap.get(dec));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String normalizeHex(String hex) {
        String clean = hex.trim().toUpperCase();
        if (clean.startsWith("0X")) {
            clean = clean.substring(2);
        }
        clean = clean.replace(":", "").replace("-", "");
        // Remove leading zeroes but leave at least one char if zero
        clean = clean.replaceFirst("^0+(?!$)", "");
        if (clean.length() % 2 != 0) {
            clean = "0" + clean;
        }
        return clean;
    }
}
