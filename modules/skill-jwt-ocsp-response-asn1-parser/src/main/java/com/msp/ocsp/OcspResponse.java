package com.msp.ocsp;

import java.util.Objects;
import java.util.Optional;

/**
 * Top-level RFC 6960 §4.2.1 {@code OCSPResponse}.
 */
public record OcspResponse(
        OcspResponseStatus status,
        Optional<String> responseTypeOid,
        Optional<BasicOcspResponse> basicResponse
) {
    public static final String ID_PKIX_OCSP_BASIC = "1.3.6.1.5.5.7.48.1.1";

    public OcspResponse {
        Objects.requireNonNull(status, "status must not be null");
        responseTypeOid = responseTypeOid == null ? Optional.empty() : responseTypeOid;
        basicResponse = basicResponse == null ? Optional.empty() : basicResponse;
    }
}
