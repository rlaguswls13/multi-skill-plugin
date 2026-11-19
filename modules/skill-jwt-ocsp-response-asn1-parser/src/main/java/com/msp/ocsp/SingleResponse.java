package com.msp.ocsp;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * RFC 6960 §4.2.1 {@code SingleResponse} entry inside {@link ResponseData}.
 */
public record SingleResponse(
        CertId certId,
        CertStatus certStatus,
        Instant thisUpdate,
        Optional<Instant> nextUpdate,
        boolean hasExtensions
) {
    public SingleResponse {
        Objects.requireNonNull(certId, "certId must not be null");
        Objects.requireNonNull(certStatus, "certStatus must not be null");
        Objects.requireNonNull(thisUpdate, "thisUpdate must not be null");
        nextUpdate = nextUpdate == null ? Optional.empty() : nextUpdate;
    }
}
