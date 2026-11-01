package com.msp.httpauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HttpAuthParser Unit Tests")
class HttpAuthParserTest {

    @Test
    @DisplayName("Successfully parse standard Basic authentication header")
    void testParseBasicStandard() {
        // "aladdin:open sesame" in base64 is "YWxhZGRpbjpvcGVuIHNlc2FtZQ=="
        String header = "Basic YWxhZGRpbjpvcGVuIHNlc2FtZQ==";
        AuthCredentials creds = HttpAuthParser.parse(header);

        assertInstanceOf(BasicAuthCredentials.class, creds);
        BasicAuthCredentials basic = (BasicAuthCredentials) creds;
        assertEquals(AuthScheme.BASIC, basic.authScheme());
        assertEquals("Basic", basic.schemeName());
        assertEquals("aladdin", basic.username());
        assertEquals("open sesame", basic.password());
    }

    @Test
    @DisplayName("Parse Basic authentication with colons inside password")
    void testParseBasicPasswordWithColons() {
        // "user:pass:word:123:abc" -> Base64
        String formatted = HttpAuthParser.formatBasic("user", "pass:word:123:abc");
        AuthCredentials creds = HttpAuthParser.parse(formatted);

        assertInstanceOf(BasicAuthCredentials.class, creds);
        BasicAuthCredentials basic = (BasicAuthCredentials) creds;
        assertEquals("user", basic.username());
        assertEquals("pass:word:123:abc", basic.password());
    }

    @Test
    @DisplayName("Format and parse Basic authentication round-trip")
    void testFormatAndParseBasicRoundTrip() {
        String formatted = HttpAuthParser.formatBasic("admin_user", "Secret#P@ssw0rd! 2026");
        assertTrue(formatted.startsWith("Basic "));

        AuthCredentials creds = HttpAuthParser.parse(formatted);
        assertInstanceOf(BasicAuthCredentials.class, creds);
        BasicAuthCredentials basic = (BasicAuthCredentials) creds;
        assertEquals("admin_user", basic.username());
        assertEquals("Secret#P@ssw0rd! 2026", basic.password());
    }

    @Test
    @DisplayName("Format Basic throws when username contains colon")
    void testFormatBasicInvalidUsernameWithColon() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                HttpAuthParser.formatBasic("user:name", "pass"));
        assertTrue(ex.getMessage().contains("username cannot contain a colon"));
    }

    @Test
    @DisplayName("Parse Basic with case-insensitive scheme")
    void testParseBasicCaseInsensitive() {
        String header = "bAsIc YWxhZGRpbjpvcGVuIHNlc2FtZQ==";
        AuthCredentials creds = HttpAuthParser.parse(header);
        assertInstanceOf(BasicAuthCredentials.class, creds);
        assertEquals(AuthScheme.BASIC, creds.authScheme());
    }

    @Test
    @DisplayName("Parse Basic fails on missing colon")
    void testParseBasicMissingColon() {
        // "usernameonly" in base64: "dXNlcm5hbWVvbmx5"
        String header = "Basic dXNlcm5hbWVvbmx5";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                HttpAuthParser.parse(header));
        assertTrue(ex.getMessage().contains("missing ':'"));
    }

    @Test
    @DisplayName("Parse Basic fails on invalid base64")
    void testParseBasicInvalidBase64() {
        String header = "Basic ???not-base64???";
        assertThrows(IllegalArgumentException.class, () -> HttpAuthParser.parse(header));
    }

    @Test
    @DisplayName("Successfully parse standard Bearer token header")
    void testParseBearerStandard() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-IDcSemACt8x4iTMCda8Yhe3iZaWbvV5XKSTbuAn0M";
        String header = "Bearer " + token;

        AuthCredentials creds = HttpAuthParser.parse(header);
        assertInstanceOf(BearerAuthCredentials.class, creds);
        BearerAuthCredentials bearer = (BearerAuthCredentials) creds;
        assertEquals(AuthScheme.BEARER, bearer.authScheme());
        assertEquals("Bearer", bearer.schemeName());
        assertEquals(token, bearer.token());
    }

    @Test
    @DisplayName("Format and parse Bearer round-trip")
    void testFormatAndParseBearerRoundTrip() {
        String rawToken = "my-secret-access-token-12345";
        String formatted = HttpAuthParser.formatBearer(rawToken);
        assertEquals("Bearer my-secret-access-token-12345", formatted);

        AuthCredentials creds = HttpAuthParser.parse(formatted);
        assertInstanceOf(BearerAuthCredentials.class, creds);
        BearerAuthCredentials bearer = (BearerAuthCredentials) creds;
        assertEquals(rawToken, bearer.token());
    }

    @Test
    @DisplayName("Parse Bearer with lowercase or uppercase scheme")
    void testParseBearerCaseInsensitive() {
        AuthCredentials creds1 = HttpAuthParser.parse("bearer tok123");
        AuthCredentials creds2 = HttpAuthParser.parse("BEARER tok123");
        assertEquals(AuthScheme.BEARER, creds1.authScheme());
        assertEquals(AuthScheme.BEARER, creds2.authScheme());
    }

    @Test
    @DisplayName("Format Bearer throws on blank or null token")
    void testFormatBearerInvalidToken() {
        assertThrows(NullPointerException.class, () -> HttpAuthParser.formatBearer(null));
        assertThrows(IllegalArgumentException.class, () -> HttpAuthParser.formatBearer("   "));
    }

    @Test
    @DisplayName("Parse Custom authorization scheme with parameters")
    void testParseCustomSchemeWithParameters() {
        String header = "Digest username=\"Mufasa\", realm=\"myrealm@host.com\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", uri=\"/dir/index.html\", response=\"6629fae49393a05397450978507c4ef1\"";
        AuthCredentials creds = HttpAuthParser.parse(header);

        assertInstanceOf(CustomAuthCredentials.class, creds);
        CustomAuthCredentials custom = (CustomAuthCredentials) creds;
        assertEquals(AuthScheme.CUSTOM, custom.authScheme());
        assertEquals("Digest", custom.scheme());
        assertEquals("Digest", custom.schemeName());

        Map<String, String> params = custom.parameters();
        assertEquals("Mufasa", params.get("username"));
        assertEquals("myrealm@host.com", params.get("realm"));
        assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", params.get("nonce"));
        assertEquals("/dir/index.html", params.get("uri"));
        assertEquals("6629fae49393a05397450978507c4ef1", params.get("response"));
    }

    @Test
    @DisplayName("Parse Custom authorization scheme with raw unparameterized token")
    void testParseCustomSchemeRawToken() {
        String header = "ApiKey secret-token-xyz-9876";
        AuthCredentials creds = HttpAuthParser.parse(header);

        assertInstanceOf(CustomAuthCredentials.class, creds);
        CustomAuthCredentials custom = (CustomAuthCredentials) creds;
        assertEquals(AuthScheme.CUSTOM, custom.authScheme());
        assertEquals("ApiKey", custom.scheme());
        assertEquals("secret-token-xyz-9876", custom.rawValue());
        assertTrue(custom.parameters().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Parse throws on null or blank headers")
    void testParseNullOrBlank(String header) {
        assertThrows(IllegalArgumentException.class, () -> HttpAuthParser.parse(header));
    }

    @Test
    @DisplayName("Parse throws when header contains scheme only without credential payload")
    void testParseSchemeOnly() {
        assertThrows(IllegalArgumentException.class, () -> HttpAuthParser.parse("Bearer"));
        assertThrows(IllegalArgumentException.class, () -> HttpAuthParser.parse("Basic "));
    }
}
