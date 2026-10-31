package com.msp.dnswire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Top-level DNS message representation according to RFC 1035 section 4.1.
 * Supports binary parsing from and serialization to RFC 1035 wire format.
 */
public record DnsMessage(
        DnsHeader header,
        List<DnsQuestion> questions,
        List<DnsResourceRecord> answers,
        List<DnsResourceRecord> authorities,
        List<DnsResourceRecord> additionals
) {
    public DnsMessage {
        Objects.requireNonNull(header, "header must not be null");
        questions = questions == null ? List.of() : List.copyOf(questions);
        answers = answers == null ? List.of() : List.copyOf(answers);
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
        additionals = additionals == null ? List.of() : List.copyOf(additionals);
    }

    /**
     * Parses a raw DNS packet in RFC 1035 wire format.
     *
     * @param wireData Raw byte array of the DNS packet
     * @return Fully parsed DnsMessage instance
     * @throws DnsParseException if the packet is corrupted, incomplete, or contains illegal pointers
     */
    public static DnsMessage parse(byte[] wireData) {
        DnsWireReader reader = new DnsWireReader(wireData);
        DnsHeader header = reader.readHeader();

        List<DnsQuestion> questions = new ArrayList<>(header.questionCount());
        for (int i = 0; i < header.questionCount(); i++) {
            questions.add(reader.readQuestion());
        }

        List<DnsResourceRecord> answers = new ArrayList<>(header.answerCount());
        for (int i = 0; i < header.answerCount(); i++) {
            answers.add(reader.readResourceRecord());
        }

        List<DnsResourceRecord> authorities = new ArrayList<>(header.authorityCount());
        for (int i = 0; i < header.authorityCount(); i++) {
            authorities.add(reader.readResourceRecord());
        }

        List<DnsResourceRecord> additionals = new ArrayList<>(header.additionalCount());
        for (int i = 0; i < header.additionalCount(); i++) {
            additionals.add(reader.readResourceRecord());
        }

        return new DnsMessage(header, questions, answers, authorities, additionals);
    }

    /**
     * Serializes this DNS message to RFC 1035 wire-format bytes with compression pointers.
     *
     * @return Byte array ready for UDP/TCP transmission
     */
    public byte[] toWire() {
        DnsWireWriter writer = new DnsWireWriter();

        // Ensure header counts match actual list sizes
        DnsHeader updatedHeader = DnsHeader.builder()
                .id(header.id())
                .response(header.response())
                .opcode(header.opcode())
                .authoritativeAnswer(header.authoritativeAnswer())
                .truncated(header.truncated())
                .recursionDesired(header.recursionDesired())
                .recursionAvailable(header.recursionAvailable())
                .responseCode(header.responseCode())
                .questionCount(questions.size())
                .answerCount(answers.size())
                .authorityCount(authorities.size())
                .additionalCount(additionals.size())
                .build();

        writer.writeHeader(updatedHeader);

        for (DnsQuestion question : questions) {
            writer.writeQuestion(question);
        }
        for (DnsResourceRecord record : answers) {
            writer.writeResourceRecord(record);
        }
        for (DnsResourceRecord record : authorities) {
            writer.writeResourceRecord(record);
        }
        for (DnsResourceRecord record : additionals) {
            writer.writeResourceRecord(record);
        }

        return writer.toByteArray();
    }

    /**
     * Convenience factory for creating a standard DNS query.
     */
    public static DnsMessage createQuery(int id, String domain, DnsRecordType type) {
        DnsHeader header = DnsHeader.builder()
                .id(id)
                .response(false)
                .opcode(0)
                .recursionDesired(true)
                .questionCount(1)
                .build();
        return new DnsMessage(
                header,
                List.of(new DnsQuestion(domain, type)),
                List.of(),
                List.of(),
                List.of()
        );
    }

    /**
     * Creates a response message responding to this query.
     */
    public DnsMessage createResponse(int responseCode, List<DnsResourceRecord> answers) {
        DnsHeader responseHeader = DnsHeader.builder()
                .id(this.header.id())
                .response(true)
                .opcode(this.header.opcode())
                .authoritativeAnswer(false)
                .truncated(false)
                .recursionDesired(this.header.recursionDesired())
                .recursionAvailable(true)
                .responseCode(responseCode)
                .questionCount(this.questions.size())
                .answerCount(answers != null ? answers.size() : 0)
                .authorityCount(0)
                .additionalCount(0)
                .build();

        return new DnsMessage(
                responseHeader,
                this.questions,
                answers != null ? answers : List.of(),
                List.of(),
                List.of()
        );
    }
}
