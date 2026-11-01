package com.msp.etag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HttpConditionalEvaluator RFC 7232 Tests")
class HttpConditionalEvaluatorTest {

    private final Instant nov06 = Instant.parse("1994-11-06T08:49:37Z");
    private final Instant nov07 = Instant.parse("1994-11-07T08:49:37Z");
    private final EntityTag v1Strong = EntityTag.strong("v1");
    private final EntityTag v1Weak = EntityTag.weak("v1");

    @Test
    @DisplayName("No conditional headers -> Always passes 200")
    void testNoConditionalHeaders() {
        HttpConditionalEvaluator evaluator = HttpConditionalEvaluator.create()
                .withEtag(v1Strong)
                .withLastModified(nov06);

        ConditionalEvaluationResult result = evaluator.evaluate(Map.of(), "GET");
        assertTrue(result.isPassed());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    @DisplayName("If-Match: Strong match succeeds, mismatch fails with 412")
    void testIfMatch() {
        HttpConditionalEvaluator evaluator = HttpConditionalEvaluator.create()
                .withEtag(v1Strong);

        // Matching strong
        var res1 = evaluator.evaluate(Map.of("If-Match", "\"v1\", \"v2\""), "PUT");
        assertTrue(res1.isPassed());
        assertEquals(200, res1.getStatusCode());

        // Mismatch
        var res2 = evaluator.evaluate(Map.of("If-Match", "\"v99\""), "PUT");
        assertFalse(res2.isPassed());
        assertEquals(412, res2.getStatusCode());
        assertEquals("If-Match", res2.getFailedHeader());

        // Weak tag in If-Match does NOT match strong target under strong comparison
        var res3 = evaluator.evaluate(Map.of("If-Match", "W/\"v1\""), "PUT");
        assertFalse(res3.isPassed());
        assertEquals(412, res3.getStatusCode());

        // Wildcard matches existing resource
        var res4 = evaluator.evaluate(Map.of("If-Match", "*"), "DELETE");
        assertTrue(res4.isPassed());

        // Wildcard fails if target does not exist
        var res5 = HttpConditionalEvaluator.create().withTargetExists(false)
                .evaluate(Map.of("If-Match", "*"), "DELETE");
        assertFalse(res5.isPassed());
        assertEquals(412, res5.getStatusCode());
    }

    @Test
    @DisplayName("If-Unmodified-Since: Fails with 412 if modified after given date")
    void testIfUnmodifiedSince() {
        HttpConditionalEvaluator evaluator = HttpConditionalEvaluator.create()
                .withLastModified(nov07);

        // Nov 07 is after Nov 06 -> modified, so precondition fails
        var res1 = evaluator.evaluate(Map.of("If-Unmodified-Since", HttpDateParser.formatRfc1123(nov06)), "PUT");
        assertFalse(res1.isPassed());
        assertEquals(412, res1.getStatusCode());

        // Nov 07 is not after Nov 07 -> unmodified, passes
        var res2 = evaluator.evaluate(Map.of("If-Unmodified-Since", HttpDateParser.formatRfc1123(nov07)), "PUT");
        assertTrue(res2.isPassed());

        // If-Match overrides If-Unmodified-Since
        var res3 = evaluator.withEtag(v1Strong).evaluate(
                Map.of("If-Match", "\"v1\"", "If-Unmodified-Since", HttpDateParser.formatRfc1123(nov06)),
                "PUT"
        );
        assertTrue(res3.isPassed(), "If-Match present causes If-Unmodified-Since to be skipped");
    }

    @Test
    @DisplayName("If-None-Match: GET/HEAD allows weak comparison and returns 304; PUT returns 412")
    void testIfNoneMatch() {
        HttpConditionalEvaluator strongTarget = HttpConditionalEvaluator.create()
                .withEtag(v1Strong);

        // GET with weak tag matching strong target -> 304 Not Modified
        var res1 = strongTarget.evaluate(Map.of("If-None-Match", "W/\"v1\""), "GET");
        assertFalse(res1.isPassed());
        assertEquals(304, res1.getStatusCode());

        // HEAD with strong tag matching strong target -> 304 Not Modified
        var res2 = strongTarget.evaluate(Map.of("If-None-Match", "\"v1\""), "HEAD");
        assertFalse(res2.isPassed());
        assertEquals(304, res2.getStatusCode());

        // PUT with weak tag does NOT match strong target under strong comparison -> passes 200
        var res3 = strongTarget.evaluate(Map.of("If-None-Match", "W/\"v1\""), "PUT");
        assertTrue(res3.isPassed());

        // PUT with strong tag matching strong target -> 412 Precondition Failed
        var res4 = strongTarget.evaluate(Map.of("If-None-Match", "\"v1\""), "PUT");
        assertFalse(res4.isPassed());
        assertEquals(412, res4.getStatusCode());

        // If-None-Match wildcard * matches existing target -> 304 on GET
        var res5 = strongTarget.evaluate(Map.of("If-None-Match", "*"), "GET");
        assertFalse(res5.isPassed());
        assertEquals(304, res5.getStatusCode());

        // If-None-Match mismatch -> 200 OK
        var res6 = strongTarget.evaluate(Map.of("If-None-Match", "\"different\""), "GET");
        assertTrue(res6.isPassed());
    }

    @Test
    @DisplayName("If-Modified-Since: GET returns 304 if not modified, 200 if modified")
    void testIfModifiedSince() {
        HttpConditionalEvaluator evaluator = HttpConditionalEvaluator.create()
                .withLastModified(nov06);

        // Client has nov06 -> not modified since nov06 -> 304
        var res1 = evaluator.evaluate(Map.of("If-Modified-Since", HttpDateParser.formatRfc1123(nov06)), "GET");
        assertFalse(res1.isPassed());
        assertEquals(304, res1.getStatusCode());

        // Resource modified nov07, client has nov06 -> modified, returns 200
        var res2 = HttpConditionalEvaluator.create().withLastModified(nov07)
                .evaluate(Map.of("If-Modified-Since", HttpDateParser.formatRfc1123(nov06)), "GET");
        assertTrue(res2.isPassed());
        assertEquals(200, res2.getStatusCode());

        // POST request ignores If-Modified-Since
        var res3 = evaluator.evaluate(Map.of("If-Modified-Since", HttpDateParser.formatRfc1123(nov06)), "POST");
        assertTrue(res3.isPassed());

        // If-None-Match present causes If-Modified-Since to be ignored
        var res4 = evaluator.withEtag(v1Strong).evaluate(
                Map.of("If-None-Match", "\"other\"", "If-Modified-Since", HttpDateParser.formatRfc1123(nov06)),
                "GET"
        );
        assertTrue(res4.isPassed(), "If-None-Match mismatch proceeds to 200, skipping If-Modified-Since");
    }

    @Test
    @DisplayName("Case-insensitive header key lookup")
    void testCaseInsensitiveHeaders() {
        HttpConditionalEvaluator evaluator = HttpConditionalEvaluator.create().withEtag(v1Strong);
        var res = evaluator.evaluate(Map.of("if-none-match", "\"v1\""), "get");
        assertFalse(res.isPassed());
        assertEquals(304, res.getStatusCode());
    }
}
