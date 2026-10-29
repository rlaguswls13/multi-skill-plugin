package com.msp.zip;

/**
 * Security threshold options for guarding against ZipBomb and excessive extraction.
 *
 * @param maxExpansionRatio Maximum allowed ratio between uncompressed size and compressed archive size.
 * @param maxTotalBytes     Maximum allowed total uncompressed bytes.
 * @param maxEntries        Maximum allowed number of entries inside archive.
 */
public record ZipGuardOptions(
        int maxExpansionRatio,
        long maxTotalBytes,
        int maxEntries
) {
    public static final int DEFAULT_MAX_EXPANSION_RATIO = 100;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 10L * 1024 * 1024 * 1024; // 10 GB
    public static final int DEFAULT_MAX_ENTRIES = 100_000;

    public static final ZipGuardOptions DEFAULT = new ZipGuardOptions(
            DEFAULT_MAX_EXPANSION_RATIO,
            DEFAULT_MAX_TOTAL_BYTES,
            DEFAULT_MAX_ENTRIES
    );

    public static ZipGuardOptions unlimited() {
        return new ZipGuardOptions(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE);
    }
}
