package com.msp.dnswire;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DnsMessageTest {

    @Test
    @DisplayName("Should create standard DNS query and serialize to wire format")
    void testCreateQueryAndSerialize() {
        DnsMessage query = DnsMessage.createQuery(0x1234, "example.com", DnsRecordType.A);
        byte[] wire = query.toWire();

        assertNotNull(wire);
        assertTrue(wire.length > 12);

        // Parse back
        DnsMessage parsed = DnsMessage.parse(wire);
        assertEquals(0x1234, parsed.header().id());
        assertFalse(parsed.header().response());
        assertTrue(parsed.header().recursionDesired());
        assertEquals(1, parsed.questions().size());

        DnsQuestion q = parsed.questions().getFirst();
        assertEquals("example.com", q.name());
        assertEquals(DnsRecordType.A.code(), q.type());
        assertEquals(DnsClass.IN, q.dnsClass());
    }

    @Test
    @DisplayName("Should parse and roundtrip DNS response with A, AAAA, CNAME, MX, and TXT records")
    void testResponseRoundtripWithMultipleRecords() {
        DnsMessage query = DnsMessage.createQuery(42, "service.example.org", DnsRecordType.A);

        DnsResourceRecord aRec = DnsResourceRecord.createA("service.example.org", 300, "192.0.2.1");
        DnsResourceRecord aaaaRec = DnsResourceRecord.createAaaa("service.example.org", 300, "2001:db8::1");
        DnsResourceRecord cnameRec = DnsResourceRecord.createCname("alias.example.org", 600, "service.example.org");
        DnsResourceRecord mxRec = DnsResourceRecord.createMx("service.example.org", 3600, 10, "mail.example.org");
        DnsResourceRecord txtRec = DnsResourceRecord.createTxt("service.example.org", 300, "v=spf1 include:_spf.example.org ~all");

        DnsMessage response = query.createResponse(DnsResponseCode.NOERROR.code(), List.of(aRec, aaaaRec, cnameRec, mxRec, txtRec));

        byte[] wire = response.toWire();
        assertNotNull(wire);

        DnsMessage parsed = DnsMessage.parse(wire);
        assertTrue(parsed.header().response());
        assertEquals(5, parsed.answers().size());

        // Check A record
        DnsResourceRecord parsedA = parsed.answers().get(0);
        assertEquals("service.example.org", parsedA.name());
        assertEquals(DnsRecordType.A, parsedA.recordType());
        assertEquals("192.0.2.1", parsedA.asIpv4Address());

        // Check AAAA record
        DnsResourceRecord parsedAaaa = parsed.answers().get(1);
        assertEquals(DnsRecordType.AAAA, parsedAaaa.recordType());
        assertTrue(parsedAaaa.asIpv6Address().contains("2001:db8"));

        // Check CNAME record
        DnsResourceRecord parsedCname = parsed.answers().get(2);
        assertEquals(DnsRecordType.CNAME, parsedCname.recordType());
        assertEquals("service.example.org", parsedCname.asDomainName());

        // Check MX record
        DnsResourceRecord parsedMx = parsed.answers().get(3);
        assertEquals(DnsRecordType.MX, parsedMx.recordType());
        DnsResourceRecord.MxData mxData = parsedMx.asMx();
        assertEquals(10, mxData.priority());
        assertEquals("mail.example.org", mxData.exchange());

        // Check TXT record
        DnsResourceRecord parsedTxt = parsed.answers().get(4);
        assertEquals(DnsRecordType.TXT, parsedTxt.recordType());
        assertEquals("v=spf1 include:_spf.example.org ~all", parsedTxt.asText());
    }

    private String asIpv4AddressSafe(DnsResourceRecord r) {
        try {
            return InetAddress.getByAddress(r.rdata()).getHostAddress();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle pointer compression and pointer jumps properly")
    void testPointerCompressionDecompression() {
        // Build raw packet with pointer manually
        // Header: 12 bytes
        byte[] raw = new byte[]{
                // ID: 0x0001
                0x00, 0x01,
                // Flags: 0x8180 (response, RD, RA, NOERROR)
                (byte) 0x81, (byte) 0x80,
                // QDCOUNT: 1
                0x00, 0x01,
                // ANCOUNT: 1
                0x00, 0x01,
                // NSCOUNT: 0, ARCOUNT: 0
                0x00, 0x00, 0x00, 0x00,
                // Question: "foo.bar.com" -> 3 'f' 'o' 'o' 3 'b' 'a' 'r' 3 'c' 'o' 'm' 0
                3, 'f', 'o', 'o', 3, 'b', 'a', 'r', 3, 'c', 'o', 'm', 0,
                // QTYPE: A (1), QCLASS: IN (1)
                0x00, 0x01, 0x00, 0x01,
                // Answer: Pointer to offset 12 (0xC00C -> foo.bar.com)
                (byte) 0xC0, 0x0C,
                // TYPE: A (1), CLASS: IN (1)
                0x00, 0x01, 0x00, 0x01,
                // TTL: 60
                0x00, 0x00, 0x00, 0x3C,
                // RDLENGTH: 4
                0x00, 0x04,
                // RDATA: 10.0.0.1
                10, 0, 0, 1
        };

        DnsMessage msg = DnsMessage.parse(raw);
        assertEquals("foo.bar.com", msg.questions().getFirst().name());
        assertEquals("foo.bar.com", msg.answers().getFirst().name());
        assertEquals("10.0.0.1", msg.answers().getFirst().asIpv4Address());
    }

    @Test
    @DisplayName("Should detect pointer cycles and throw DnsParseException")
    void testPointerCycleDetection() {
        byte[] cyclicPacket = new byte[]{
                0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                // Pointer to offset 14 (itself)
                (byte) 0xC0, 0x0E,
                // Pointer back to 12
                (byte) 0xC0, 0x0C
        };

        assertThrows(DnsParseException.class, () -> DnsMessage.parse(cyclicPacket));
    }

    @Test
    @DisplayName("Should throw DnsParseException on truncated packet or out-of-bounds pointer")
    void testTruncatedPacket() {
        // Less than 12 bytes
        assertThrows(DnsParseException.class, () -> DnsMessage.parse(new byte[]{1, 2, 3}));

        // Pointer points past end of packet
        byte[] invalidPointerPacket = new byte[]{
                0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                (byte) 0xC0, (byte) 0xFE
        };
        assertThrows(DnsParseException.class, () -> DnsMessage.parse(invalidPointerPacket));
    }
}
