package com.msp.dnswire;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * DNS Resource Record (RR) according to RFC 1035 section 4.1.3.
 *
 * @param name     Domain name to which this resource record pertains
 * @param type     Two-octet RR type code (e.g. 1=A, 28=AAAA, 5=CNAME, 15=MX, 16=TXT)
 * @param dnsClass Two-octet class code (usually IN=1)
 * @param ttl      32-bit unsigned time to live (seconds)
 * @param rdata    Raw binary RDATA bytes
 */
public record DnsResourceRecord(
        String name,
        int type,
        int dnsClass,
        long ttl,
        byte[] rdata
) {
    public DnsResourceRecord {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(rdata, "rdata must not be null");
        if (dnsClass <= 0) {
            dnsClass = DnsClass.IN;
        }
        rdata = rdata.clone();
    }

    @Override
    public byte[] rdata() {
        return rdata.clone();
    }

    public DnsRecordType recordType() {
        return DnsRecordType.fromCode(type);
    }

    public int rdLength() {
        return rdata.length;
    }

    // --- High-level Typed Helpers ---

    public record MxData(int priority, String exchange) {}

    /**
     * Parses RDATA as an IPv4 address string (e.g. "192.0.2.1").
     */
    public String asIpv4Address() {
        if (type != DnsRecordType.A.code() || rdata.length != 4) {
            throw new IllegalStateException("Record is not an A record or invalid length: " + rdata.length);
        }
        return (rdata[0] & 0xFF) + "." + (rdata[1] & 0xFF) + "." +
               (rdata[2] & 0xFF) + "." + (rdata[3] & 0xFF);
    }

    /**
     * Parses RDATA as an IPv6 address string (e.g. "2001:db8::1").
     */
    public String asIpv6Address() {
        if (type != DnsRecordType.AAAA.code() || rdata.length != 16) {
            throw new IllegalStateException("Record is not an AAAA record or invalid length: " + rdata.length);
        }
        try {
            return InetAddress.getByAddress(rdata).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Malformed IPv6 address bytes", e);
        }
    }

    /**
     * Parses RDATA as a domain name (for CNAME, NS, PTR records).
     */
    public String asDomainName() {
        return DnsWireReader.readDomainNameFromRdata(rdata);
    }

    /**
     * Parses RDATA as an MX record containing priority and mail exchange domain.
     */
    public MxData asMx() {
        if (type != DnsRecordType.MX.code() || rdata.length < 3) {
            throw new IllegalStateException("Record is not an MX record or too short");
        }
        int priority = ((rdata[0] & 0xFF) << 8) | (rdata[1] & 0xFF);
        byte[] domainBytes = Arrays.copyOfRange(rdata, 2, rdata.length);
        String exchange = DnsWireReader.readDomainNameFromRdata(domainBytes);
        return new MxData(priority, exchange);
    }

    /**
     * Parses RDATA as text strings (RFC 1035 character-strings for TXT record).
     */
    public String asText() {
        if (rdata.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        boolean structured = true;
        while (offset < rdata.length) {
            int len = rdata[offset] & 0xFF;
            if (offset + 1 + len > rdata.length) {
                structured = false;
                break;
            }
            sb.append(new String(rdata, offset + 1, len, StandardCharsets.UTF_8));
            offset += 1 + len;
        }
        if (structured && offset == rdata.length) {
            return sb.toString();
        }
        return new String(rdata, StandardCharsets.UTF_8);
    }

    // --- Static Factory Methods ---

    public static DnsResourceRecord createA(String name, long ttl, String ipv4) {
        try {
            InetAddress addr = InetAddress.getByName(ipv4);
            if (!(addr instanceof Inet4Address)) {
                throw new IllegalArgumentException("Not an IPv4 address: " + ipv4);
            }
            return new DnsResourceRecord(name, DnsRecordType.A.code(), DnsClass.IN, ttl, addr.getAddress());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ipv4, e);
        }
    }

    public static DnsResourceRecord createAaaa(String name, long ttl, String ipv6) {
        try {
            InetAddress addr = InetAddress.getByName(ipv6);
            if (!(addr instanceof Inet6Address)) {
                throw new IllegalArgumentException("Not an IPv6 address: " + ipv6);
            }
            return new DnsResourceRecord(name, DnsRecordType.AAAA.code(), DnsClass.IN, ttl, addr.getAddress());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv6 address: " + ipv6, e);
        }
    }

    public static DnsResourceRecord createCname(String name, long ttl, String targetDomain) {
        byte[] targetWire = DnsWireWriter.domainNameToWire(targetDomain);
        return new DnsResourceRecord(name, DnsRecordType.CNAME.code(), DnsClass.IN, ttl, targetWire);
    }

    public static DnsResourceRecord createNs(String name, long ttl, String nsDomain) {
        byte[] nsWire = DnsWireWriter.domainNameToWire(nsDomain);
        return new DnsResourceRecord(name, DnsRecordType.NS.code(), DnsClass.IN, ttl, nsWire);
    }

    public static DnsResourceRecord createMx(String name, long ttl, int priority, String exchange) {
        byte[] domainWire = DnsWireWriter.domainNameToWire(exchange);
        byte[] rdata = new byte[2 + domainWire.length];
        rdata[0] = (byte) ((priority >> 8) & 0xFF);
        rdata[1] = (byte) (priority & 0xFF);
        System.arraycopy(domainWire, 0, rdata, 2, domainWire.length);
        return new DnsResourceRecord(name, DnsRecordType.MX.code(), DnsClass.IN, ttl, rdata);
    }

    public static DnsResourceRecord createTxt(String name, long ttl, String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int offset = 0;
        if (textBytes.length == 0) {
            baos.write(0);
        } else {
            while (offset < textBytes.length) {
                int chunkSize = Math.min(255, textBytes.length - offset);
                baos.write(chunkSize);
                baos.write(textBytes, offset, chunkSize);
                offset += chunkSize;
            }
        }
        return new DnsResourceRecord(name, DnsRecordType.TXT.code(), DnsClass.IN, ttl, baos.toByteArray());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DnsResourceRecord that)) return false;
        return type == that.type &&
               dnsClass == that.dnsClass &&
               ttl == that.ttl &&
               name.equalsIgnoreCase(that.name) &&
               Arrays.equals(rdata, that.rdata);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name.toLowerCase(), type, dnsClass, ttl);
        result = 31 * result + Arrays.hashCode(rdata);
        return result;
    }
}
