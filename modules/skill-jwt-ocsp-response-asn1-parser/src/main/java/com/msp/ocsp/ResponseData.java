package com.msp.ocsp;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * RFC 6960 §4.2.1 {@code ResponseData} (the TBS portion of {@link BasicOcspResponse}).
 */
public record ResponseData(
        int version,
        ResponderId responderId,
        Instant producedAt,
        List<SingleResponse> responses,
        boolean hasExtensions,
        byte[] rawTbsBytes
) {
    public ResponseData {
        Objects.requireNonNull(responderId, "responderId must not be null");
        Objects.requireNonNull(producedAt, "producedAt must not be null");
        Objects.requireNonNull(responses, "responses must not be null");
        Objects.requireNonNull(rawTbsBytes, "rawTbsBytes must not be null");
        responses = List.copyOf(responses);
        rawTbsBytes = rawTbsBytes.clone();
    }

    @Override
    public byte[] rawTbsBytes() {
        return rawTbsBytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResponseData other)) return false;
        return version == other.version
                && hasExtensions == other.hasExtensions
                && responderId.equals(other.responderId)
                && producedAt.equals(other.producedAt)
                && responses.equals(other.responses)
                && Arrays.equals(rawTbsBytes, other.rawTbsBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, responderId, producedAt, responses, hasExtensions);
        result = 31 * result + Arrays.hashCode(rawTbsBytes);
        return result;
    }
}
