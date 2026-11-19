package com.msp.ocsp;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * RFC 6960 §4.2.1 {@code CertStatus} CHOICE ({@code good}, {@code revoked}, {@code unknown}).
 */
public sealed interface CertStatus permits CertStatus.Good, CertStatus.Revoked, CertStatus.Unknown {

    record Good() implements CertStatus {
    }

    record Revoked(Instant revocationTime, Optional<RevocationReason> reason) implements CertStatus {
        public Revoked {
            Objects.requireNonNull(revocationTime, "revocationTime must not be null");
            reason = reason == null ? Optional.empty() : reason;
        }
    }

    record Unknown() implements CertStatus {
    }
}
