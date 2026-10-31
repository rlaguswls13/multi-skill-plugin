package com.msp.dns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DnsRecordResolver Tests")
class DnsRecordResolverTest {

    @Test
    @DisplayName("DnsRecordType enum values verification")
    void testDnsRecordTypes() {
        assertEquals(7, DnsRecordType.values().length);
        assertNotNull(DnsRecordType.valueOf("A"));
        assertNotNull(DnsRecordType.valueOf("AAAA"));
        assertNotNull(DnsRecordType.valueOf("MX"));
        assertNotNull(DnsRecordType.valueOf("TXT"));
        assertNotNull(DnsRecordType.valueOf("CNAME"));
        assertNotNull(DnsRecordType.valueOf("NS"));
        assertNotNull(DnsRecordType.valueOf("PTR"));
    }

    @Test
    @DisplayName("DnsRecord record validation and properties")
    void testDnsRecordProperties() {
        DnsRecord record = new DnsRecord("example.com", DnsRecordType.A, "93.184.216.34", 300);
        assertEquals("example.com", record.host());
        assertEquals(DnsRecordType.A, record.type());
        assertEquals("93.184.216.34", record.value());
        assertEquals(300, record.ttlSeconds());

        // Negative TTL gets clamped to 0
        DnsRecord recordNegativeTtl = new DnsRecord("example.com", DnsRecordType.A, "93.184.216.34", -10);
        assertEquals(0, recordNegativeTtl.ttlSeconds());

        // Null checks
        assertThrows(NullPointerException.class, () -> new DnsRecord(null, DnsRecordType.A, "val"));
        assertThrows(NullPointerException.class, () -> new DnsRecord("host", null, "val"));
        assertThrows(NullPointerException.class, () -> new DnsRecord("host", DnsRecordType.A, null));
    }

    @Test
    @DisplayName("DnsLookupResult success and failure factory methods")
    void testDnsLookupResult() {
        DnsRecord r1 = new DnsRecord("test.org", DnsRecordType.A, "10.0.0.1", 60);
        DnsLookupResult successResult = DnsLookupResult.success("test.org", DnsRecordType.A, List.of(r1), 15);

        assertTrue(successResult.success());
        assertNull(successResult.errorMessage());
        assertEquals(1, successResult.records().size());
        assertEquals("10.0.0.1", successResult.records().getFirst().value());
        assertEquals(15, successResult.elapsedMillis());

        DnsLookupResult failureResult = DnsLookupResult.failure("unknown.invalid", DnsRecordType.TXT, "Host not found", 20);
        assertFalse(failureResult.success());
        assertEquals("Host not found", failureResult.errorMessage());
        assertTrue(failureResult.records().isEmpty());
    }

    @Test
    @DisplayName("DnsRecordResolver null and blank argument validation")
    void testValidation() {
        DnsRecordResolver resolver = new DnsRecordResolver();
        assertThrows(NullPointerException.class, () -> resolver.resolve(null, DnsRecordType.A));
        assertThrows(NullPointerException.class, () -> resolver.resolve("example.com", null));

        DnsLookupResult emptyHostResult = resolver.resolve("   ", DnsRecordType.A);
        assertFalse(emptyHostResult.success());
        assertEquals("Host cannot be empty", emptyHostResult.errorMessage());

        assertTrue(resolver.resolveA(null).isEmpty());
        assertTrue(resolver.resolveA("").isEmpty());
        assertTrue(resolver.resolveTxt(null).isEmpty());
        assertTrue(resolver.resolveTxt("").isEmpty());
    }

    @Test
    @DisplayName("resolveA resolves localhost or loopback via fallback")
    void testResolveLocalhost() {
        DnsRecordResolver resolver = new DnsRecordResolver();
        List<String> aRecords = resolver.resolveA("localhost");
        assertNotNull(aRecords);
        assertFalse(aRecords.isEmpty(), "Localhost should resolve to at least one IPv4 address");
        assertTrue(aRecords.contains("127.0.0.1"));
    }

    @Test
    @DisplayName("DnsRecordResolver handles non-existent or invalid domains gracefully without throwing")
    void testGracefulFailureHandling() {
        // Use a definitely non-existent domain with fast timeout
        DnsRecordResolver resolver = new DnsRecordResolver("127.0.0.1", 500, 1);
        DnsLookupResult result = resolver.resolve("non-existent-subdomain-test-xyz12345.invalid", DnsRecordType.A);

        assertNotNull(result);
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertTrue(result.records().isEmpty());
    }

    @Test
    @DisplayName("Custom DNS server constructor configurations")
    void testCustomDnsServerConstructor() {
        DnsRecordResolver customResolver = new DnsRecordResolver("8.8.8.8");
        assertNotNull(customResolver);

        DnsRecordResolver customWithTimeout = new DnsRecordResolver("1.1.1.1", 1000, 2);
        assertNotNull(customWithTimeout);
    }
}
