package com.msp.dnsreverse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class FcrdnsValidatorTest {

    private InMemoryDnsResolver dns;
    private FcrdnsValidator validator;

    @BeforeEach
    void setUp() {
        dns = new InMemoryDnsResolver();
        validator = FcrdnsValidator.inMemory(dns);
    }

    @Test
    @DisplayName("Should successfully confirm valid FCrDNS (IP -> domain -> same IP)")
    void testValidFcrdns() throws Exception {
        InetAddress ip = InetAddress.getByName("198.51.100.25");
        String domain = "mail.company.org";

        dns.addPtrRecord(ip, domain);
        dns.addARecord(domain, ip);

        FcrdnsValidationResult result = validator.validate(ip);

        assertTrue(result.isConfirmed());
        assertEquals(FcrdnsStatus.CONFIRMED, result.status());
        assertEquals(1, result.confirmedDomains().size());
        assertEquals("mail.company.org", result.confirmedDomains().get(0));
    }

    @Test
    @DisplayName("Should confirm when domain resolves to multiple IPs including client IP")
    void testMultipleARecordsMatching() throws Exception {
        InetAddress clientIp = InetAddress.getByName("198.51.100.10");
        InetAddress otherIp = InetAddress.getByName("198.51.100.11");
        String domain = "mta.example.net";

        dns.addPtrRecord(clientIp, domain);
        dns.addARecord(domain, otherIp);
        dns.addARecord(domain, clientIp); // client IP is in the list

        FcrdnsValidationResult result = validator.validate("198.51.100.10");

        assertTrue(result.isConfirmed());
        assertEquals(FcrdnsStatus.CONFIRMED, result.status());
    }

    @Test
    @DisplayName("Should detect spoofed PTR record (anti-spoofing IP mismatch)")
    void testSpoofedPtrRecordMismatch() throws Exception {
        InetAddress attackerIp = InetAddress.getByName("203.0.113.99");
        InetAddress realServerIp = InetAddress.getByName("198.51.100.5");
        String spoofedDomain = "secure.bank.com";

        // Attacker's PTR points to legitimate bank domain
        dns.addPtrRecord(attackerIp, spoofedDomain);
        // Bank domain forward DNS points to genuine server IP
        dns.addARecord(spoofedDomain, realServerIp);

        FcrdnsValidationResult result = validator.validate(attackerIp);

        assertFalse(result.isConfirmed());
        assertEquals(FcrdnsStatus.IP_MISMATCH, result.status());
        assertTrue(result.confirmedDomains().isEmpty());
    }

    @Test
    @DisplayName("Should handle missing PTR record")
    void testMissingPtrRecord() throws Exception {
        InetAddress ip = InetAddress.getByName("192.0.2.1");

        FcrdnsValidationResult result = validator.validate(ip);

        assertFalse(result.isConfirmed());
        assertEquals(FcrdnsStatus.NO_PTR_RECORD, result.status());
    }

    @Test
    @DisplayName("Should handle broken forward DNS lookup")
    void testBrokenForwardLookup() throws Exception {
        InetAddress ip = InetAddress.getByName("192.0.2.2");
        dns.addPtrRecord(ip, "ghost.example.com");
        // No A/AAAA record registered for ghost.example.com

        FcrdnsValidationResult result = validator.validate(ip);

        assertFalse(result.isConfirmed());
        assertEquals(FcrdnsStatus.FORWARD_LOOKUP_FAILED, result.status());
    }

    @Test
    @DisplayName("Should support IPv6 FCrDNS validation")
    void testIpv6Fcrdns() throws Exception {
        InetAddress ipv6 = InetAddress.getByName("2001:db8::cafe:1");
        String domain = "v6mail.example.org";

        dns.addPtrRecord(ipv6, domain);
        dns.addAaaaRecord(domain, ipv6);

        FcrdnsValidationResult result = validator.validate(ipv6);

        assertTrue(result.isConfirmed());
        assertEquals(FcrdnsStatus.CONFIRMED, result.status());
        assertEquals("v6mail.example.org", result.confirmedDomains().get(0));
    }

    @Test
    @DisplayName("Should fail gracefully on malformed IP string")
    void testMalformedIpString() {
        FcrdnsValidationResult result = validator.validate("999.999.999.999");
        assertFalse(result.isConfirmed());
        assertEquals(FcrdnsStatus.DNS_ERROR, result.status());
    }
}
