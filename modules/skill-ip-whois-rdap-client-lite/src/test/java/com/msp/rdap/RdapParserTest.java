package com.msp.rdap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RDAP Client Lite & WHOIS Formatter Tests")
class RdapParserTest {

    @Test
    @DisplayName("SimpleJsonParser correctly parses objects, arrays, and primitives")
    void testSimpleJsonParser() {
        String json = """
                {
                  "name": "TEST",
                  "count": 42,
                  "ratio": 3.14,
                  "active": true,
                  "extra": null,
                  "tags": ["a", "b", "c"],
                  "nested": { "k": "v\\"escaped\\"" }
                }
                """;
        Map<String, Object> map = SimpleJsonParser.parseObject(json);
        assertEquals("TEST", map.get("name"));
        assertEquals(42L, map.get("count"));
        assertEquals(3.14, map.get("ratio"));
        assertEquals(true, map.get("active"));
        assertNull(map.get("extra"));
    }

    @Test
    @DisplayName("RdapQueryBuilder builds RFC 7482 compliant query URLs")
    void testQueryBuilder() {
        RdapQueryBuilder builder = RdapQueryBuilder.create();
        assertEquals(URI.create("https://rdap.arin.net/registry/ip/192.0.2.1"), builder.buildIpQuery("192.0.2.1"));
        assertEquals(URI.create("https://rdap.arin.net/registry/ip/192.0.2.0/24"), builder.buildIpQuery("192.0.2.0/24"));
        assertEquals(URI.create("https://rdap.arin.net/registry/autnum/15169"), builder.buildAutnumQuery(15169));
        assertEquals(URI.create("https://rdap.arin.net/registry/domain/example.com"), builder.buildDomainQuery("example.com"));

        RdapQueryBuilder ripe = RdapQueryBuilder.withBase(RdapQueryBuilder.RIPE_BASE);
        assertEquals(URI.create("https://rdap.db.ripe.net/ip/193.0.0.1"), ripe.buildIpQuery("193.0.0.1"));
    }

    @Test
    @DisplayName("Parses IPv4 RDAP network response with vCard and events")
    void testParseIpv4Network() {
        String json = """
                {
                  "handle": "NET-192-0-2-0-1",
                  "startAddress": "192.0.2.0",
                  "endAddress": "192.0.2.255",
                  "ipVersion": "v4",
                  "name": "TEST-NET-1",
                  "type": "ALLOCATED",
                  "country": "US",
                  "status": ["active"],
                  "port43": "whois.arin.net",
                  "events": [
                    { "eventAction": "registration", "eventDate": "2009-06-11T12:00:00Z" },
                    { "eventAction": "last changed", "eventDate": "2020-03-01T15:30:00Z" }
                  ],
                  "entities": [
                    {
                      "handle": "ARIN-ABUSE",
                      "roles": ["abuse"],
                      "vcardArray": [
                        "vcard",
                        [
                          ["version", {}, "text", "4.0"],
                          ["fn", {}, "text", "Abuse Department"],
                          ["org", {}, "text", "IANA / RFC 5737"],
                          ["email", {}, "text", "abuse@example.com"],
                          ["tel", {}, "text", "+1-555-555-0100"],
                          ["adr", {}, "text", ["", "", "12025 Waterfront Dr", "Los Angeles", "CA", "90292", "US"]]
                        ]
                      ]
                    }
                  ]
                }
                """;

        RdapNetworkRecord net = RdapParser.parseNetwork(json);
        assertEquals("NET-192-0-2-0-1", net.handle());
        assertEquals("192.0.2.0", net.startAddress());
        assertEquals("192.0.2.255", net.endAddress());
        assertEquals("v4", net.ipVersion());
        assertEquals("TEST-NET-1", net.name());
        assertEquals("US", net.country());
        assertTrue(net.status().contains("active"));

        // Events
        assertEquals(Instant.parse("2009-06-11T12:00:00Z"), net.events().get("registration"));
        assertEquals(Instant.parse("2020-03-01T15:30:00Z"), net.events().get("last changed"));

        // Entities & vCard
        assertEquals(1, net.entities().size());
        RdapEntityRecord entity = net.entities().get(0);
        assertEquals("ARIN-ABUSE", entity.handle());
        assertTrue(entity.roles().contains("abuse"));
        assertEquals("Abuse Department", entity.formattedName());
        assertEquals("IANA / RFC 5737", entity.organization());
        assertEquals("abuse@example.com", entity.email());
        assertEquals("+1-555-555-0100", entity.phone());
        assertTrue(entity.address().contains("12025 Waterfront Dr"));

        // WHOIS Formatter
        String whois = WhoisFormatter.formatNetwork(net);
        assertTrue(whois.contains("NetRange"));
        assertTrue(whois.contains("192.0.2.0 - 192.0.2.255"));
        assertTrue(whois.contains("NetName"));
        assertTrue(whois.contains("abuse@example.com"));
    }

    @Test
    @DisplayName("Parses Autnum / ASN RDAP response and formats WHOIS")
    void testParseAutnum() {
        String json = """
                {
                  "handle": "AS15169",
                  "startAutnum": 15169,
                  "endAutnum": 15169,
                  "name": "GOOGLE",
                  "type": "DIRECT ALLOCATION",
                  "country": "US",
                  "status": ["active"],
                  "port43": "whois.arin.net",
                  "entities": [
                    {
                      "handle": "GOGL",
                      "roles": ["registrant"],
                      "vcardArray": [
                        "vcard",
                        [
                          ["fn", {}, "text", "Google LLC"],
                          ["org", {}, "text", "Google LLC"]
                        ]
                      ]
                    }
                  ]
                }
                """;

        RdapAutnumRecord asn = RdapParser.parseAutnum(json);
        assertEquals("AS15169", asn.handle());
        assertEquals(15169, asn.startAutnum());
        assertEquals(15169, asn.endAutnum());
        assertEquals("GOOGLE", asn.name());

        String whois = WhoisFormatter.formatAutnum(asn);
        assertTrue(whois.contains("ASNumber"));
        assertTrue(whois.contains("15169"));
        assertTrue(whois.contains("ASName"));
        assertTrue(whois.contains("GOOGLE"));
        assertTrue(whois.contains("Google LLC"));
    }
}
