package com.msp.encodingsniffer;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Result of encoding sniffing with detected charset, confidence score, and BOM indicator.
 *
 * @param charset    Detected {@link Charset}
 * @param confidence Confidence score between 0.0 (no confidence) and 1.0 (certainty)
 * @param hasBom     Whether a Byte Order Mark (BOM) was found in the header
 */
public record EncodingConfidence(
        Charset charset,
        double confidence,
        boolean hasBom
) {
    public EncodingConfidence {
        Objects.requireNonNull(charset, "charset must not be null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0: " + confidence);
        }
    }
}
