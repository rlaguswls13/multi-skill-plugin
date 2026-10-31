package com.msp.dnswire;

/**
 * DNS message header according to RFC 1035 section 4.1.1.
 * Total size is always 12 bytes on the wire.
 *
 * @param id                   16-bit identifier assigned by the program that generates any kind of query
 * @param response             QR bit: false = Query (0), true = Response (1)
 * @param opcode               4-bit operation code (0 = standard query QUERY)
 * @param authoritativeAnswer  AA bit: true if responding name server is an authority for domain
 * @param truncated            TC bit: true if message was truncated due to length exceeding transmission channel
 * @param recursionDesired     RD bit: directs name server to pursue query recursively
 * @param recursionAvailable   RA bit: set or cleared in response denoting whether recursive query support is available
 * @param responseCode         4-bit RCODE (0 = NOERROR, 3 = NXDOMAIN, etc.)
 * @param questionCount        QDCOUNT: number of entries in question section
 * @param answerCount          ANCOUNT: number of resource records in answer section
 * @param authorityCount       NSCOUNT: number of name server resource records in authority section
 * @param additionalCount      ARCOUNT: number of resource records in additional records section
 */
public record DnsHeader(
        int id,
        boolean response,
        int opcode,
        boolean authoritativeAnswer,
        boolean truncated,
        boolean recursionDesired,
        boolean recursionAvailable,
        int responseCode,
        int questionCount,
        int answerCount,
        int authorityCount,
        int additionalCount
) {
    public static final int HEADER_LENGTH = 12;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private boolean response;
        private int opcode;
        private boolean authoritativeAnswer;
        private boolean truncated;
        private boolean recursionDesired = true;
        private boolean recursionAvailable;
        private int responseCode;
        private int questionCount;
        private int answerCount;
        private int authorityCount;
        private int additionalCount;

        public Builder id(int id) {
            this.id = id & 0xFFFF;
            return this;
        }

        public Builder response(boolean response) {
            this.response = response;
            return this;
        }

        public Builder opcode(int opcode) {
            this.opcode = opcode & 0x0F;
            return this;
        }

        public Builder authoritativeAnswer(boolean authoritativeAnswer) {
            this.authoritativeAnswer = authoritativeAnswer;
            return this;
        }

        public Builder truncated(boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public Builder recursionDesired(boolean recursionDesired) {
            this.recursionDesired = recursionDesired;
            return this;
        }

        public Builder recursionAvailable(boolean recursionAvailable) {
            this.recursionAvailable = recursionAvailable;
            return this;
        }

        public Builder responseCode(int responseCode) {
            this.responseCode = responseCode & 0x0F;
            return this;
        }

        public Builder questionCount(int questionCount) {
            this.questionCount = questionCount;
            return this;
        }

        public Builder answerCount(int answerCount) {
            this.answerCount = answerCount;
            return this;
        }

        public Builder authorityCount(int authorityCount) {
            this.authorityCount = authorityCount;
            return this;
        }

        public Builder additionalCount(int additionalCount) {
            this.additionalCount = additionalCount;
            return this;
        }

        public DnsHeader build() {
            return new DnsHeader(
                    id, response, opcode, authoritativeAnswer, truncated,
                    recursionDesired, recursionAvailable, responseCode,
                    questionCount, answerCount, authorityCount, additionalCount
            );
        }
    }
}
