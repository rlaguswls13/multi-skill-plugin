package com.msp.jwtextract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtClaimsExtractorTest {

    private String createToken(String headerJson, String payloadJson) {
        String encHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = "fake-signature-part";
        return encHeader + "." + encPayload + "." + signature;
    }

    @Test
    @DisplayName("표준 JWT Header 파싱 검증")
    void testParseHeader() {
        String header = """
                {"alg": "RS256", "typ": "JWT", "kid": "key-01"}
                """;
        String payload = """
                {"sub": "alice"}
                """;
        String token = createToken(header, payload);

        JwtHeader jwtHeader = JwtClaimsExtractor.parseHeader(token);
        assertEquals("RS256", jwtHeader.alg());
        assertEquals("JWT", jwtHeader.typ());
        assertEquals("key-01", jwtHeader.getClaim("kid"));
    }

    @Test
    @DisplayName("표준 JWT Payload 및 Subject 추출 검증")
    void testParsePayloadAndSubject() {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = """
                {
                    "sub": "user_42",
                    "iss": "https://auth.example.com",
                    "aud": "api://backend",
                    "exp": 2500000000,
                    "iat": 1700000000,
                    "jti": "jwt-uuid-1234",
                    "department": "Engineering"
                }
                """;
        String token = createToken(header, payload);

        JwtPayload parsed = JwtClaimsExtractor.parse(token);
        assertEquals("user_42", parsed.sub());
        assertEquals("https://auth.example.com", parsed.iss());
        assertEquals("api://backend", parsed.aud());
        assertEquals(Instant.ofEpochSecond(2500000000L), parsed.exp());
        assertEquals(Instant.ofEpochSecond(1700000000L), parsed.iat());
        assertEquals("jwt-uuid-1234", parsed.jti());
        assertEquals("Engineering", parsed.getString("department"));

        Optional<String> subject = JwtClaimsExtractor.getSubject(token);
        assertTrue(subject.isPresent());
        assertEquals("user_42", subject.get());
    }

    @Test
    @DisplayName("토큰 만료 여부 판별 검증 (isExpired)")
    void testIsExpired() {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

        // 만료된 토큰 (2020년경)
        String expiredPayload = "{\"sub\":\"bob\",\"exp\":1577836800}";
        String expiredToken = createToken(header, expiredPayload);
        assertTrue(JwtClaimsExtractor.isExpired(expiredToken));

        // 미래 토큰 (2050년경)
        String validPayload = "{\"sub\":\"bob\",\"exp\":2524608000}";
        String validToken = createToken(header, validPayload);
        assertFalse(JwtClaimsExtractor.isExpired(validToken));

        // exp 클레임이 없는 경우 -> false
        String noExpPayload = "{\"sub\":\"bob\"}";
        String noExpToken = createToken(header, noExpPayload);
        assertFalse(JwtClaimsExtractor.isExpired(noExpToken));
    }

    @Test
    @DisplayName("역할/권한 목록(getRoleList) 다양한 포맷 파싱 검증")
    void testGetRoleListFormats() {
        String header = "{\"alg\":\"HS256\"}";

        // 1. JSON Array 형태
        String tokenArray = createToken(header, "{\"roles\":[\"ADMIN\", \"MANAGER\", \"USER\"]}");
        List<String> roles1 = JwtClaimsExtractor.getRoleList(tokenArray, "roles");
        assertEquals(List.of("ADMIN", "MANAGER", "USER"), roles1);

        // 2. Comma separated string 형태
        String tokenComma = createToken(header, "{\"roles\":\"ADMIN, MANAGER, USER\"}");
        List<String> roles2 = JwtClaimsExtractor.getRoleList(tokenComma, "roles");
        assertEquals(List.of("ADMIN", "MANAGER", "USER"), roles2);

        // 3. Space separated scope 형태
        String tokenScope = createToken(header, "{\"scope\":\"read write delete\"}");
        List<String> scopes = JwtClaimsExtractor.getRoleList(tokenScope, "scope");
        assertEquals(List.of("read", "write", "delete"), scopes);

        // 4. 존재하지 않는 클레임
        List<String> empty = JwtClaimsExtractor.getRoleList(tokenArray, "non_existent");
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("임의 클레임 추출 (getClaim)")
    void testGetClaim() {
        String header = "{\"alg\":\"HS256\"}";
        String payload = "{\"sub\":\"charlie\",\"age\":29,\"active\":true,\"balance\":123.45}";
        String token = createToken(header, payload);

        assertEquals(29L, JwtClaimsExtractor.getClaim(token, "age"));
        assertEquals(Boolean.TRUE, JwtClaimsExtractor.getClaim(token, "active"));
        assertEquals(123.45, JwtClaimsExtractor.getClaim(token, "balance"));
        assertNull(JwtClaimsExtractor.getClaim(token, "not_found"));
    }

    @Test
    @DisplayName("잘못된 토큰 형식 예외 처리 검증")
    void testMalformedTokens() {
        assertThrows(IllegalArgumentException.class, () -> JwtClaimsExtractor.parse(null));
        assertThrows(IllegalArgumentException.class, () -> JwtClaimsExtractor.parse(""));
        assertThrows(IllegalArgumentException.class, () -> JwtClaimsExtractor.parse("single-part-token"));
        assertThrows(IllegalArgumentException.class, () -> JwtClaimsExtractor.parse("invalid!base64.part2"));
    }

    @Test
    @DisplayName("SimpleJsonParser 중첩 및 특수문자 파싱 검증")
    void testSimpleJsonParserDirectly() {
        String json = """
                {
                    "name": "John \\"The Boss\\" Doe",
                    "nested": {
                        "level": 1,
                        "enabled": false
                    },
                    "tags": ["alpha", "beta"],
                    "nullField": null
                }
                """;
        var map = SimpleJsonParser.parseObject(json);
        assertEquals("John \"The Boss\" Doe", map.get("name"));
        assertTrue(map.get("nested") instanceof java.util.Map);
        assertNull(map.get("nullField"));
    }
}
