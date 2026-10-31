package com.msp.dnswire;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Binary serializer for DNS wire-format packets with RFC 1035 pointer compression.
 */
public final class DnsWireWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final Map<String, Integer> domainOffsets = new HashMap<>();

    public DnsWireWriter() {}

    public int position() {
        return out.size();
    }

    public void writeByte(int b) {
        out.write(b & 0xFF);
    }

    public void writeShort(int s) {
        out.write((s >> 8) & 0xFF);
        out.write(s & 0xFF);
    }

    public void writeInt(long v) {
        out.write((int) ((v >> 24) & 0xFF));
        out.write((int) ((v >> 16) & 0xFF));
        out.write((int) ((v >> 8) & 0xFF));
        out.write((int) (v & 0xFF));
    }

    public void writeBytes(byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            out.write(bytes, 0, bytes.length);
        }
    }

    public void writeHeader(DnsHeader header) {
        writeShort(header.id());

        int flags = 0;
        if (header.response()) flags |= 0x8000;
        flags |= (header.opcode() & 0x0F) << 11;
        if (header.authoritativeAnswer()) flags |= 0x0400;
        if (header.truncated()) flags |= 0x0200;
        if (header.recursionDesired()) flags |= 0x0100;
        if (header.recursionAvailable()) flags |= 0x0080;
        flags |= (header.responseCode() & 0x0F);

        writeShort(flags);
        writeShort(header.questionCount());
        writeShort(header.answerCount());
        writeShort(header.authorityCount());
        writeShort(header.additionalCount());
    }

    public void writeDomainName(String domain) {
        writeDomainName(domain, true);
    }

    public void writeDomainName(String domain, boolean compress) {
        if (domain == null || domain.isBlank() || domain.equals(".")) {
            writeByte(0);
            return;
        }

        String normalized = domain.endsWith(".") ? domain.substring(0, domain.length() - 1) : domain;
        String remaining = normalized.toLowerCase();

        while (!remaining.isEmpty()) {
            if (compress) {
                Integer offset = domainOffsets.get(remaining);
                if (offset != null && offset < 0x4000) {
                    // Write pointer (0xC000 | offset)
                    int ptr = 0xC000 | (offset & 0x3FFF);
                    writeShort(ptr);
                    return;
                }
            }

            int currentPos = out.size();
            if (compress && currentPos < 0x4000) {
                domainOffsets.put(remaining, currentPos);
            }

            int dotIndex = remaining.indexOf('.');
            String label;
            if (dotIndex == -1) {
                label = remaining;
                remaining = "";
            } else {
                label = remaining.substring(0, dotIndex);
                remaining = remaining.substring(dotIndex + 1);
            }

            byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
            if (labelBytes.length > 63) {
                throw new IllegalArgumentException("DNS label exceeds 63 octets: " + label);
            }
            writeByte(labelBytes.length);
            writeBytes(labelBytes);
        }

        // Terminal zero octet
        writeByte(0);
    }

    public void writeQuestion(DnsQuestion question) {
        writeDomainName(question.name());
        writeShort(question.type());
        writeShort(question.dnsClass());
    }

    public void writeResourceRecord(DnsResourceRecord record) {
        writeDomainName(record.name());
        writeShort(record.type());
        writeShort(record.dnsClass());
        writeInt(record.ttl());
        byte[] rdata = record.rdata();
        writeShort(rdata.length);
        writeBytes(rdata);
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    public static byte[] domainNameToWire(String domain) {
        if (domain == null || domain.isBlank() || domain.equals(".")) {
            return new byte[]{0};
        }
        DnsWireWriter writer = new DnsWireWriter();
        writer.writeDomainName(domain, false);
        return writer.toByteArray();
    }
}
