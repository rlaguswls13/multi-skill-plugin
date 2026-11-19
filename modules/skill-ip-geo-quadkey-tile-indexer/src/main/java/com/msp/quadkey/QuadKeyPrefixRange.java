package com.msp.quadkey;

/**
 * Represents a contiguous lexicographical QuadKey prefix range {@code [startQuadKey, endQuadKey]}
 * suitable for B-Tree / LSM-Tree spatial range scans.
 */
public record QuadKeyPrefixRange(String prefix, String minQuadKeyAtZoom, String maxQuadKeyAtZoom, int targetZoom) {
    public boolean matches(String candidateQuadKey) {
        return candidateQuadKey != null && candidateQuadKey.startsWith(prefix);
    }
}
