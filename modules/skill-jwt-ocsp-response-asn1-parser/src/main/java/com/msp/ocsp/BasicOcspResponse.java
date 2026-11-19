package com.msp.ocsp;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * RFC 6960 §4.2.1 {@code BasicOCSPResponse}.
 */
public record BasicOcspResponse(
        ResponseData tbsResponseData,
        String signatureAlgorithmOid,
        byte[] signature,
        List<byte[]> certificates
) {
    public BasicOcspResponse {
        Objects.requireNonNull(tbsResponseData, "tbsResponseData must not be null");
        Objects.requireNonNull(signatureAlgorithmOid, "signatureAlgorithmOid must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        signature = signature.clone();
        certificates = certificates == null
                ? List.of()
                : certificates.stream().map(byte[]::clone).toList();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    @Override
    public List<byte[]> certificates() {
        return certificates.stream().map(byte[]::clone).toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BasicOcspResponse other)) return false;
        if (!tbsResponseData.equals(other.tbsResponseData)
                || !signatureAlgorithmOid.equals(other.signatureAlgorithmOid)
                || !Arrays.equals(signature, other.signature)
                || certificates.size() != other.certificates.size()) {
            return false;
        }
        for (int i = 0; i < certificates.size(); i++) {
            if (!Arrays.equals(certificates.get(i), other.certificates.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(tbsResponseData, signatureAlgorithmOid, certificates.size());
        result = 31 * result + Arrays.hashCode(signature);
        return result;
    }
}
