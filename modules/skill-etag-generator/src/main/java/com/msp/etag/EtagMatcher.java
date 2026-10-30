package com.msp.etag;

import java.util.Arrays;
import java.util.Objects;

/**
 * Utility to validate conditional HTTP requests using If-None-Match and If-Match headers.
 */
public final class EtagMatcher {

    private EtagMatcher() {}

    /**
     * Checks if the resource matches the 'If-None-Match' header according to RFC 7232 weak comparison.
     * If matched, server should return 304 Not Modified.
     *
     * @param ifNoneMatchHeader the HTTP If-None-Match request header value (e.g., "\"abc\", \"def\"" or "*")
     * @param currentEtag       the current resource's ETag
     * @return true if matches (cache is fresh -> 304), false otherwise (serve content -> 200)
     */
    public static boolean matchesIfNoneMatch(String ifNoneMatchHeader, Etag currentEtag) {
        if (ifNoneMatchHeader == null || ifNoneMatchHeader.isBlank() || currentEtag == null) {
            return false;
        }

        String trimmed = ifNoneMatchHeader.trim();
        if ("*".equals(trimmed)) {
            return true;
        }

        String currentTagOnly = currentEtag.tag();

        // Split comma-separated ETags
        String[] headerTags = trimmed.split(",");
        for (String candidate : headerTags) {
            String cleanCandidate = cleanTag(candidate.trim());
            if (cleanCandidate.equals(currentTagOnly)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper that parses string currentEtag into Etag and evaluates matchesIfNoneMatch.
     */
    public static boolean matchesIfNoneMatch(String ifNoneMatchHeader, String currentEtagString) {
        if (currentEtagString == null || currentEtagString.isBlank()) {
            return false;
        }
        return matchesIfNoneMatch(ifNoneMatchHeader, new Etag(currentEtagString, false));
    }

    /**
     * Strips leading W/, quotes, and trailing spaces from an ETag string.
     */
    private static String cleanTag(String raw) {
        String s = raw.trim();
        if (s.startsWith("W/\"") || s.startsWith("w/\"")) {
            s = s.substring(3);
        } else if (s.startsWith("\"")) {
            s = s.substring(1);
        }
        if (s.endsWith("\"")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
