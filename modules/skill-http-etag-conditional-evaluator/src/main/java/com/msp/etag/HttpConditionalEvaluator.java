package com.msp.etag;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * RFC 7232 Conditional Request Evaluation Engine.
 *
 * <p>Implements RFC 7232 Section 6: Precedence of Preconditions:
 * <ol>
 *   <li><b>If-Match</b>: Strong comparison. If fail &rarr; 412. If pass &rarr; skip If-Unmodified-Since, go to 3.</li>
 *   <li><b>If-Unmodified-Since</b>: Evaluated only if no If-Match. If modified after &rarr; 412.</li>
 *   <li><b>If-None-Match</b>: Weak comparison for GET/HEAD; Strong for others. If matches: GET/HEAD &rarr; 304, others &rarr; 412. If none match &rarr; skip If-Modified-Since, go to 5.</li>
 *   <li><b>If-Modified-Since</b>: Evaluated only if no If-None-Match and method is GET/HEAD. If not modified &rarr; 304.</li>
 *   <li><b>Preconditions Passed</b> &rarr; 200 OK (passed).</li>
 * </ol>
 */
public final class HttpConditionalEvaluator {

    public static final String HEADER_IF_MATCH = "If-Match";
    public static final String HEADER_IF_NONE_MATCH = "If-None-Match";
    public static final String HEADER_IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String HEADER_IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

    private final EntityTag currentEtag;
    private final Instant lastModified;
    private final boolean targetExists;

    private HttpConditionalEvaluator(EntityTag currentEtag, Instant lastModified, boolean targetExists) {
        this.currentEtag = currentEtag;
        this.lastModified = lastModified;
        this.targetExists = targetExists;
    }

    public static HttpConditionalEvaluator create() {
        return new HttpConditionalEvaluator(null, null, true);
    }

    public HttpConditionalEvaluator withEtag(EntityTag etag) {
        return new HttpConditionalEvaluator(etag, this.lastModified, this.targetExists);
    }

    public HttpConditionalEvaluator withEtag(String rawEtag) {
        return new HttpConditionalEvaluator(rawEtag != null ? EntityTag.parse(rawEtag) : null, this.lastModified, this.targetExists);
    }

    public HttpConditionalEvaluator withLastModified(Instant lastModified) {
        return new HttpConditionalEvaluator(this.currentEtag, lastModified, this.targetExists);
    }

    public HttpConditionalEvaluator withTargetExists(boolean targetExists) {
        return new HttpConditionalEvaluator(this.currentEtag, this.lastModified, targetExists);
    }

    /**
     * Evaluates HTTP headers against current resource state according to RFC 7232 Section 6.
     *
     * @param headers HTTP request headers (case-insensitive lookup)
     * @param httpMethod HTTP method (GET, HEAD, PUT, POST, DELETE, etc.)
     * @return ConditionalEvaluationResult
     */
    public ConditionalEvaluationResult evaluate(Map<String, String> headers, String httpMethod) {
        String method = httpMethod != null ? httpMethod.trim().toUpperCase(Locale.ROOT) : "GET";
        boolean isSafeMethod = "GET".equals(method) || "HEAD".equals(method);

        String ifMatch = getHeaderCaseInsensitive(headers, HEADER_IF_MATCH);
        String ifUnmodifiedSince = getHeaderCaseInsensitive(headers, HEADER_IF_UNMODIFIED_SINCE);
        String ifNoneMatch = getHeaderCaseInsensitive(headers, HEADER_IF_NONE_MATCH);
        String ifModifiedSince = getHeaderCaseInsensitive(headers, HEADER_IF_MODIFIED_SINCE);

        return evaluate(ifMatch, ifNoneMatch, ifModifiedSince, ifUnmodifiedSince, isSafeMethod);
    }

    /**
     * Explicit overload allowing individual header values to be passed directly.
     */
    public ConditionalEvaluationResult evaluate(
            String ifMatch,
            String ifNoneMatch,
            String ifModifiedSince,
            String ifUnmodifiedSince,
            boolean isSafeMethod
    ) {
        // Step 1: When If-Match is present
        if (ifMatch != null && !ifMatch.isBlank()) {
            List<EntityTag> ifMatchList = EntityTag.parseList(ifMatch);
            if (!ifMatchList.isEmpty()) {
                boolean matched = evaluateIfMatch(ifMatchList);
                if (!matched) {
                    return ConditionalEvaluationResult.preconditionFailed(
                            HEADER_IF_MATCH,
                            "If-Match precondition failed: none of the specified ETags strongly matched current resource"
                    );
                }
                // When If-Match is present and true, RFC 7232 Step 1 says: proceed to Step 3 (skip If-Unmodified-Since)
                return evaluateIfNoneMatchOrSubsequent(ifNoneMatch, ifModifiedSince, isSafeMethod);
            }
        }

        // Step 2: When If-Unmodified-Since is present (and no If-Match)
        if (ifUnmodifiedSince != null && !ifUnmodifiedSince.isBlank()) {
            Optional<Instant> parsedDate = HttpDateParser.parse(ifUnmodifiedSince);
            if (parsedDate.isPresent()) {
                if (targetExists && lastModified != null) {
                    Instant lmSec = lastModified.truncatedTo(ChronoUnit.SECONDS);
                    Instant reqSec = parsedDate.get().truncatedTo(ChronoUnit.SECONDS);
                    if (lmSec.isAfter(reqSec)) {
                        return ConditionalEvaluationResult.preconditionFailed(
                                HEADER_IF_UNMODIFIED_SINCE,
                                "If-Unmodified-Since precondition failed: resource was modified after " + ifUnmodifiedSince
                        );
                    }
                }
            }
        }

        // Proceed to Step 3
        return evaluateIfNoneMatchOrSubsequent(ifNoneMatch, ifModifiedSince, isSafeMethod);
    }

    private ConditionalEvaluationResult evaluateIfNoneMatchOrSubsequent(
            String ifNoneMatch,
            String ifModifiedSince,
            boolean isSafeMethod
    ) {
        // Step 3: When If-None-Match is present
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            List<EntityTag> ifNoneMatchList = EntityTag.parseList(ifNoneMatch);
            if (!ifNoneMatchList.isEmpty()) {
                boolean matchesNone = evaluateIfNoneMatch(ifNoneMatchList, isSafeMethod);
                if (!matchesNone) {
                    // Match found -> Precondition failed
                    if (isSafeMethod) {
                        return ConditionalEvaluationResult.notModified(
                                HEADER_IF_NONE_MATCH,
                                "If-None-Match matched current resource ETag; returning 304 Not Modified"
                        );
                    } else {
                        return ConditionalEvaluationResult.preconditionFailed(
                                HEADER_IF_NONE_MATCH,
                                "If-None-Match matched current resource ETag for non-safe method; returning 412 Precondition Failed"
                        );
                    }
                }
                // If If-None-Match condition is true (no match), RFC 7232 says proceed to Step 5 (skip If-Modified-Since)
                return ConditionalEvaluationResult.passed();
            }
        }

        // Step 4: When If-Modified-Since is present (and no If-None-Match), and method is GET or HEAD
        if (isSafeMethod && ifModifiedSince != null && !ifModifiedSince.isBlank()) {
            Optional<Instant> parsedDate = HttpDateParser.parse(ifModifiedSince);
            if (parsedDate.isPresent()) {
                if (targetExists && lastModified != null) {
                    Instant lmSec = lastModified.truncatedTo(ChronoUnit.SECONDS);
                    Instant reqSec = parsedDate.get().truncatedTo(ChronoUnit.SECONDS);
                    // RFC 7232: if last modification date is NOT more recent than date provided, condition is false -> 304
                    if (!lmSec.isAfter(reqSec)) {
                        return ConditionalEvaluationResult.notModified(
                                HEADER_IF_MODIFIED_SINCE,
                                "Resource has not been modified since " + ifModifiedSince + "; returning 304 Not Modified"
                        );
                    }
                }
            }
        }

        // Step 5: Perform requested method
        return ConditionalEvaluationResult.passed();
    }

    private boolean evaluateIfMatch(List<EntityTag> tags) {
        if (!targetExists) {
            return false;
        }
        for (EntityTag tag : tags) {
            if (tag.isWildcard()) {
                return true;
            }
            if (currentEtag != null && currentEtag.strongCompare(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateIfNoneMatch(List<EntityTag> tags, boolean isSafeMethod) {
        if (!targetExists) {
            return true; // None match because resource does not exist
        }
        for (EntityTag tag : tags) {
            if (tag.isWildcard()) {
                // If resource exists, wildcard matches -> not "none match"
                return false;
            }
            if (currentEtag != null) {
                boolean match = isSafeMethod ? currentEtag.weakCompare(tag) : currentEtag.strongCompare(tag);
                if (match) {
                    return false; // Found a match!
                }
            }
        }
        return true;
    }

    private static String getHeaderCaseInsensitive(Map<String, String> headers, String targetHeader) {
        if (headers == null || headers.isEmpty() || targetHeader == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (targetHeader.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
