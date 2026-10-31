package com.msp.dnswire;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Low-level binary reader for DNS wire-format packets with pointer compression support.
 */
public final class DnsWireReader {
    private final byte[] wire;
    private int pos;

    public DnsWireReader(byte[] wire) {
        if (wire == null || wire.length < DnsHeader.HEADER_LENGTH) {
            throw new DnsParseException("Wire data is null or smaller than DNS header (12 bytes)");
        }
        this.wire = wire;
        this.pos = 0;
    }

    public int position() {
        return pos;
    }

    public int remaining() {
        return wire.length - pos;
    }

    public int readByte() {
        if (pos >= wire.length) {
            throw new DnsParseException("Unexpected end of DNS packet at position " + pos);
        }
        return wire[pos++] & 0xFF;
    }

    public int readShort() {
        return (readByte() << 8) | readByte();
    }

    public long readInt() {
        return ((long) readByte() << 24) |
               ((long) readByte() << 16) |
               ((long) readByte() << 8) |
               ((long) readByte());
    }

    public byte[] readBytes(int length) {
        if (length < 0 || pos + length > wire.length) {
            throw new DnsParseException("Cannot read " + length + " bytes, only " + remaining() + " remaining");
        }
        byte[] result = new byte[length];
        System.arraycopy(wire, pos, result, 0, length);
        pos += length;
        return result;
    }

    public DnsHeader readHeader() {
        int id = readShort();
        int flags = readShort();
        int qdcount = readShort();
        int ancount = readShort();
        int nscount = readShort();
        int arcount = readShort();

        boolean response = (flags & 0x8000) != 0;
        int opcode = (flags >> 11) & 0x0F;
        boolean authoritativeAnswer = (flags & 0x0400) != 0;
        boolean truncated = (flags & 0x0200) != 0;
        boolean recursionDesired = (flags & 0x0100) != 0;
        boolean recursionAvailable = (flags & 0x0080) != 0;
        int rcode = flags & 0x000F;

        return new DnsHeader(
                id, response, opcode, authoritativeAnswer, truncated,
                recursionDesired, recursionAvailable, rcode,
                qdcount, ancount, nscount, arcount
        );
    }

    public String readDomainName() {
        Set<Integer> visitedPointers = new HashSet<>();
        List<String> labels = new ArrayList<>();
        int currentPos = this.pos;
        int resumePos = -1;

        while (true) {
            if (currentPos >= wire.length) {
                throw new DnsParseException("Domain name reading exceeded packet boundary at " + currentPos);
            }
            int len = wire[currentPos] & 0xFF;

            if (len == 0) {
                // End of domain name
                currentPos++;
                if (resumePos == -1) {
                    resumePos = currentPos;
                }
                break;
            } else if ((len & 0xC0) == 0xC0) {
                // Pointer compression (0xC0..)
                if (currentPos + 1 >= wire.length) {
                    throw new DnsParseException("Truncated pointer at position " + currentPos);
                }
                int pointerOffset = ((len & 0x3F) << 8) | (wire[currentPos + 1] & 0xFF);
                if (pointerOffset >= wire.length) {
                    throw new DnsParseException("Pointer offset out of bounds: " + pointerOffset);
                }
                if (!visitedPointers.add(pointerOffset)) {
                    throw new DnsParseException("Cycle detected in DNS pointer compression at " + pointerOffset);
                }
                if (resumePos == -1) {
                    resumePos = currentPos + 2;
                }
                currentPos = pointerOffset;
            } else if (len <= 63) {
                currentPos++;
                if (currentPos + len > wire.length) {
                    throw new DnsParseException("Label length " + len + " exceeds wire boundary at " + currentPos);
                }
                labels.add(new String(wire, currentPos, len, StandardCharsets.US_ASCII));
                currentPos += len;
            } else {
                throw new DnsParseException("Invalid label length prefix: 0x" + Integer.toHexString(len));
            }
        }

        this.pos = resumePos;
        return String.join(".", labels);
    }

    public DnsQuestion readQuestion() {
        String name = readDomainName();
        int type = readShort();
        int dnsClass = readShort();
        return new DnsQuestion(name, type, dnsClass);
    }

    public DnsResourceRecord readResourceRecord() {
        String name = readDomainName();
        int type = readShort();
        int dnsClass = readShort();
        long ttl = readInt() & 0xFFFFFFFFL;
        int rdLength = readShort();

        int rdataStart = this.pos;
        byte[] rdata;

        // If domain name records or MX may contain compression pointers, decompress them
        if (type == DnsRecordType.CNAME.code() || type == DnsRecordType.NS.code() || type == DnsRecordType.PTR.code()) {
            String domain = readDomainName();
            rdata = DnsWireWriter.domainNameToWire(domain);
            this.pos = rdataStart + rdLength;
        } else if (type == DnsRecordType.MX.code()) {
            if (rdLength < 3) {
                throw new DnsParseException("Malformed MX record length: " + rdLength);
            }
            int priority = readShort();
            String exchange = readDomainName();
            byte[] domainWire = DnsWireWriter.domainNameToWire(exchange);
            rdata = new byte[2 + domainWire.length];
            rdata[0] = (byte) ((priority >> 8) & 0xFF);
            rdata[1] = (byte) (priority & 0xFF);
            System.arraycopy(domainWire, 0, rdata, 2, domainWire.length);
            this.pos = rdataStart + rdLength;
        } else {
            rdata = readBytes(rdLength);
        }

        return new DnsResourceRecord(name, type, dnsClass, ttl, rdata);
    }

    public static String readDomainNameFromRdata(byte[] rdata) {
        if (rdata == null || rdata.length == 0) {
            return "";
        }
        List<String> labels = new ArrayList<>();
        int p = 0;
        while (p < rdata.length) {
            int len = rdata[p++] & 0xFF;
            if (len == 0) {
                break;
            }
            if (p + len > rdata.length) {
                // If not standard wire-formatted, fallback to string representation
                return new String(rdata, StandardCharsets.US_ASCII);
            }
            labels.add(new String(rdata, p, len, StandardCharsets.US_ASCII));
            p += len;
        }
        return String.join(".", labels);
    }
}
