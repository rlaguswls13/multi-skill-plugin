package com.msp.problemdetails;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Problem Details RFC 7807 / RFC 9457 Tests")
class ProblemDetailsTest {

    @Test
    @DisplayName("Default builder sets about:blank type and canonical status title")
    void testDefaults() {
        ProblemDetails problem = ProblemDetails.forStatus(404);

        assertEquals(URI.create("about:blank"), problem.getType());
        assertEquals("Not Found", problem.getTitle());
        assertEquals(404, problem.getStatus());
        assertNull(problem.getDetail());
        assertNull(problem.getInstance());
        assertTrue(problem.getExtensions().isEmpty());
    }

    @Test
    @DisplayName("Fluent builder builds complete problem details with custom extensions")
    void testFluentBuilder() {
        ProblemDetails problem = ProblemDetails.builder()
                .type("https://example.com/probs/out-of-credit")
                .title("You do not have enough credit.")
                .status(403)
                .detail("Your current balance is 30, but that costs 50.")
                .instance("/account/12345/msgs/abc")
                .extension("balance", 30)
                .extension("cost", 50)
                .extension("currency", "USD")
                .build();

        assertEquals(URI.create("https://example.com/probs/out-of-credit"), problem.getType());
        assertEquals("You do not have enough credit.", problem.getTitle());
        assertEquals(403, problem.getStatus());
        assertEquals("Your current balance is 30, but that costs 50.", problem.getDetail());
        assertEquals(URI.create("/account/12345/msgs/abc"), problem.getInstance());
        assertEquals(30, problem.getExtension("balance"));
        assertEquals(50, problem.getExtension("cost"));
        assertEquals("USD", problem.getExtension("currency"));
    }

    @Test
    @DisplayName("Serializes to JSON and parses back correctly (round-trip)")
    void testSerializationAndParsingRoundTrip() {
        ProblemDetails original = ProblemDetails.builder()
                .type(URI.create("https://api.example.com/errors/validation"))
                .title("Validation Failed")
                .status(400)
                .detail("One or more fields failed validation.")
                .instance(URI.create("/requests/req-987"))
                .extension("traceId", "tr-123456")
                .invalidParam("email", "Must be a well-formed email address")
                .invalidParam("age", "Must be at least 18")
                .build();

        String json = original.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"status\":400"));
        assertTrue(json.contains("\"title\":\"Validation Failed\""));
        assertTrue(json.contains("\"type\":\"https://api.example.com/errors/validation\""));
        assertTrue(json.contains("\"invalidParams\":["));

        ProblemDetails parsed = ProblemDetails.fromJson(json);

        assertEquals(original.getType(), parsed.getType());
        assertEquals(original.getTitle(), parsed.getTitle());
        assertEquals(original.getStatus(), parsed.getStatus());
        assertEquals(original.getDetail(), parsed.getDetail());
        assertEquals(original.getInstance(), parsed.getInstance());
        assertEquals("tr-123456", parsed.getExtension("traceId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) parsed.getExtension("invalidParams");
        assertNotNull(invalidParams);
        assertEquals(2, invalidParams.size());
        assertEquals("email", invalidParams.get(0).get("name"));
        assertEquals("Must be a well-formed email address", invalidParams.get(0).get("reason"));
    }

    @Test
    @DisplayName("Convenience factory methods create expected status and titles")
    void testConvenienceFactoryMethods() {
        ProblemDetails badReq = ProblemDetails.badRequest("Invalid query parameter");
        assertEquals(400, badReq.getStatus());
        assertEquals("Bad Request", badReq.getTitle());
        assertEquals("Invalid query parameter", badReq.getDetail());

        ProblemDetails unauth = ProblemDetails.unauthorized("Bearer token expired");
        assertEquals(401, unauth.getStatus());
        assertEquals("Unauthorized", unauth.getTitle());

        ProblemDetails forbidden = ProblemDetails.forbidden("Access denied");
        assertEquals(403, forbidden.getStatus());
        assertEquals("Forbidden", forbidden.getTitle());

        ProblemDetails notFound = ProblemDetails.notFound("User 42 does not exist");
        assertEquals(404, notFound.getStatus());
        assertEquals("Not Found", notFound.getTitle());

        ProblemDetails conflict = ProblemDetails.conflict("Email already in use");
        assertEquals(409, conflict.getStatus());
        assertEquals("Conflict", conflict.getTitle());

        ProblemDetails unproc = ProblemDetails.unprocessableEntity("Semantic error in payload");
        assertEquals(422, unproc.getStatus());
        assertEquals("Unprocessable Content", unproc.getTitle());

        ProblemDetails serverErr = ProblemDetails.internalServerError("Database connection timed out");
        assertEquals(500, serverErr.getStatus());
        assertEquals("Internal Server Error", serverErr.getTitle());
    }

    @Test
    @DisplayName("ProblemDetailsException wraps problem details properly")
    void testExceptionWrapping() {
        ProblemDetails pd = ProblemDetails.notFound("Resource 99 not found");
        ProblemDetailsException ex = new ProblemDetailsException(pd);

        assertEquals(pd, ex.getProblemDetails());
        assertTrue(ex.getMessage().contains("Not Found"));
        assertTrue(ex.getMessage().contains("Resource 99 not found"));
    }

    @Test
    @DisplayName("Json parser handles escape sequences, unicode and whitespace")
    void testJsonParserEscapesAndWhitespace() {
        String json = """
                {
                   "type": "https://example.com/err",
                   "title": "Error with \\"quotes\\" and \\n newline",
                   "status": 500,
                   "detail": "Unicode: \\u0041\\u0042\\u0043 and slashes \\/ and \\\\",
                   "nested": {
                       "active": true,
                       "inactive": false,
                       "score": 99.5
                   }
                }
                """;

        ProblemDetails pd = ProblemDetails.fromJson(json);
        assertEquals(500, pd.getStatus());
        assertEquals("Error with \"quotes\" and \n newline", pd.getTitle());
        assertEquals("Unicode: ABC and slashes / and \\", pd.getDetail());
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) pd.getExtension("nested");
        assertNotNull(nested);
        assertEquals(true, nested.get("active"));
        assertEquals(false, nested.get("inactive"));
        assertEquals(99.5, nested.get("score"));
    }
}
