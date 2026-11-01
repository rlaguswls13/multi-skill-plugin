package com.msp.forwarded;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ForwardedHeaderParser Unit Tests")
class ForwardedHeaderParserTest {

    @Test
    @DisplayName("Parse standard RFC 7239 multi-hop header")
    void testParseRfc7239MultiHop() {
        String header = "for=192.0.2.43, for=198.51.100.17;proto=https;host=example.com";
        List<ForwardedElement> elements = ForwardedHeaderParser.parseRfc7239(header);

        assertEquals(2, elements.size());

        ForwardedElement hop1 = elements.get(0);
        assertEquals("192.0.2.43", hop1.forAddress());
        assertEquals("192.0.2.43", hop1.forIp());
        assertEquals("192.0.2.43", hop1.clientIp());
        assertNull(hop1.proto());
        assertNull(hop1.host());
        assertNull(hop1.by());

        ForwardedElement hop2 = elements.get(1);
        assertEquals("198.51.100.17", hop2.forAddress());
        assertEquals("https", hop2.proto());
        assertEquals("example.com", hop2.host());
    }

    @Test
    @DisplayName("Parse RFC 7239 with bracketed IPv6 and ports")
    void testParseRfc7239Ipv6WithPort() {
        String header = "for=\"[2001:db8::1]:8080\";by=\"[2001:db8::2]:443\";host=\"api.internal:8443\"";
        List<ForwardedElement> elements = ForwardedHeaderParser.parseRfc7239(header);

        assertEquals(1, elements.size());
        ForwardedElement el = elements.get(0);

        assertEquals("[2001:db8::1]:8080", el.forAddress());
        assertEquals("2001:db8::1", el.forIp());
        assertEquals(OptionalInt.of(8080), el.forPort());
        assertTrue(el.isForIpv6());

        assertEquals("[2001:db8::2]:443", el.by());
        assertEquals("2001:db8::2", el.byIp());
        assertEquals(OptionalInt.of(443), el.byPort());

        assertEquals("api.internal:8443", el.host());
    }

    @Test
    @DisplayName("Parse RFC 7239 with escaped characters and custom extensions")
    void testParseRfc7239EscapedAndExtensions() {
        String header = "for=10.0.0.1;proto=https;custom_ext=\"escaped \\\"value\\\" test\";trace-id=abc-123";
        List<ForwardedElement> elements = ForwardedHeaderParser.parseRfc7239(header);

        assertEquals(1, elements.size());
        ForwardedElement el = elements.get(0);

        assertEquals("10.0.0.1", el.forAddress());
        assertEquals("https", el.proto());
        assertEquals("escaped \"value\" test", el.extensions().get("custom_ext"));
        assertEquals("abc-123", el.extensions().get("trace-id"));
    }

    @Test
    @DisplayName("Parse case-insensitive parameter names")
    void testParseCaseInsensitiveKeys() {
        String header = "FOR=192.168.1.1;PROTO=HTTP;BY=_edgeProxy;HOST=example.org";
        List<ForwardedElement> elements = ForwardedHeaderParser.parseRfc7239(header);

        assertEquals(1, elements.size());
        ForwardedElement el = elements.get(0);

        assertEquals("192.168.1.1", el.forAddress());
        assertEquals("HTTP", el.proto());
        assertEquals("_edgeProxy", el.by());
        assertEquals("example.org", el.host());
    }

    @Test
    @DisplayName("Parse legacy X-Forwarded-* headers")
    void testParseLegacy() {
        String xff = "203.0.113.195, 198.51.100.10";
        String proto = "https";
        String host = "mysite.com";
        String port = "443";

        List<ForwardedElement> elements = ForwardedHeaderParser.parseLegacy(xff, proto, host, port);

        assertEquals(2, elements.size());

        ForwardedElement hop1 = elements.get(0);
        assertEquals("203.0.113.195", hop1.forAddress());
        assertEquals("203.0.113.195", hop1.clientIp());
        assertEquals("https", hop1.proto());
        assertEquals("mysite.com:443", hop1.host());

        ForwardedElement hop2 = elements.get(1);
        assertEquals("198.51.100.10", hop2.forAddress());
        assertEquals("https", hop2.proto());
        assertEquals("mysite.com:443", hop2.host());
    }

    @Test
    @DisplayName("getClientIp prioritization: RFC 7239 over XFF")
    void testGetClientIpPriority() {
        String rfc = "for=198.51.100.1;proto=https, for=192.0.2.1";
        String xff = "10.0.0.99, 10.0.0.100";

        // RFC takes precedence
        String clientIp = ForwardedHeaderParser.getClientIp(rfc, xff);
        assertEquals("198.51.100.1", clientIp);

        // Fallback to XFF when RFC is null or blank
        assertEquals("10.0.0.99", ForwardedHeaderParser.getClientIp(null, xff));
        assertEquals("10.0.0.99", ForwardedHeaderParser.getClientIp("", xff));

        // When both null or blank
        assertNull(ForwardedHeaderParser.getClientIp(null, null));
        assertNull(ForwardedHeaderParser.getClientIp("", ""));
    }

    @Test
    @DisplayName("getClientIp strips IPv4 port and bracketed IPv6")
    void testGetClientIpCleaning() {
        assertEquals("203.0.113.195", ForwardedHeaderParser.getClientIp(null, "203.0.113.195:8080, 10.0.0.1"));
        assertEquals("2001:db8::1", ForwardedHeaderParser.getClientIp(null, "[2001:db8::1]:443, 10.0.0.1"));
        assertEquals("2001:db8::1", ForwardedHeaderParser.getClientIp("for=\"[2001:db8::1]:9000\"", null));
    }

    @Test
    @DisplayName("Serialization to RFC 7239 and roundtrip parsing")
    void testToRfc7239SerializationAndRoundtrip() {
        ForwardedElement hop1 = new ForwardedElement("_gateway", "192.0.2.43", "example.com:8080", "https");
        ForwardedElement hop2 = new ForwardedElement(null, "[2001:db8::1]:8080", null, "http", Map.of("secret", "xyz"));

        String serialized = ForwardedHeaderParser.toRfc7239(List.of(hop1, hop2));
        assertNotNull(serialized);
        assertTrue(serialized.contains("by=_gateway"));
        assertTrue(serialized.contains("for=192.0.2.43"));
        assertTrue(serialized.contains("host=\"example.com:8080\""));
        assertTrue(serialized.contains("for=\"[2001:db8::1]:8080\""));
        assertTrue(serialized.contains("secret=xyz"));

        // Round-trip parse
        List<ForwardedElement> parsed = ForwardedHeaderParser.parseRfc7239(serialized);
        assertEquals(2, parsed.size());
        assertEquals("_gateway", parsed.get(0).by());
        assertEquals("192.0.2.43", parsed.get(0).forAddress());
        assertEquals("example.com:8080", parsed.get(0).host());
        assertEquals("https", parsed.get(0).proto());

        assertEquals("[2001:db8::1]:8080", parsed.get(1).forAddress());
        assertEquals("2001:db8::1", parsed.get(1).forIp());
        assertEquals(OptionalInt.of(8080), parsed.get(1).forPort());
        assertEquals("xyz", parsed.get(1).extensions().get("secret"));
    }

    @Test
    @DisplayName("Graceful handling of empty or malformed headers")
    void testEmptyAndMalformed() {
        assertTrue(ForwardedHeaderParser.parseRfc7239(null).isEmpty());
        assertTrue(ForwardedHeaderParser.parseRfc7239("").isEmpty());
        assertTrue(ForwardedHeaderParser.parseRfc7239("   ").isEmpty());
        assertTrue(ForwardedHeaderParser.parseRfc7239(";;;,;,").isEmpty());
        assertTrue(ForwardedHeaderParser.parseRfc7239("invalid_without_equals").isEmpty());

        assertEquals("", ForwardedHeaderParser.toRfc7239(null));
        assertEquals("", ForwardedHeaderParser.toRfc7239(List.of()));
    }
}
