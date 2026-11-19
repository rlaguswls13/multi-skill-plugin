package com.msp.tlvcodec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BerTlvCodecTest {

    private final BerTlvCodec codec = new BerTlvCodec();

    @Test
    @DisplayName("TlvTag encodes and parses single-byte and multi-byte tags across all tag classes")
    void tagEncodingAndParsing() {
        TlvTag seq = TlvTag.of(TagClass.UNIVERSAL, true, 16);
        assertEquals("30", seq.toHex());
        assertEquals(TagClass.UNIVERSAL, seq.tagClass());
        assertTrue(seq.isConstructed());
        assertEquals(16, seq.tagNumber());

        TlvTag emvCryptogram = TlvTag.fromHex("9F26");
        assertEquals(TagClass.CONTEXT_SPECIFIC, emvCryptogram.tagClass());
        assertTrue(emvCryptogram.isPrimitive());
        assertEquals(0x26, emvCryptogram.tagNumber());
        assertEquals(emvCryptogram, TlvTag.of(TagClass.CONTEXT_SPECIFIC, false, 0x26));

        // 3-byte high tag number (e.g. tag number 200 -> 0x81 0x48)
        TlvTag threeByte = TlvTag.of(TagClass.PRIVATE, true, 200);
        assertEquals("FF8148", threeByte.toHex());
        assertEquals(threeByte, TlvTag.fromHex("FF8148"));
    }

    @Test
    @DisplayName("EMV FCI Template (6F -> 84 + A5 -> 50 + 9F26) round-trips cleanly")
    void emvConstructedTreeRoundTrip() {
        ConstructedTlv fci = TlvBuilder.constructedHex("6F")
                .primitiveHex("84", "A0000000031010")
                .nestedHex("A5", a5 -> a5
                        .primitiveUtf8(TlvTag.fromHex("50"), "VISA CREDIT")
                        .primitiveHex("9F26", "0102030405060708"))
                .build();

        byte[] encoded = codec.encode(fci);
        TlvNode decoded = codec.decode(encoded);
        assertEquals(fci, decoded);

        ConstructedTlv decodedRoot = assertInstanceOf(ConstructedTlv.class, decoded);
        PrimitiveTlv appLabel = assertInstanceOf(PrimitiveTlv.class,
                decodedRoot.findFirst(TlvTag.fromHex("50")).orElseThrow());
        assertEquals("VISA CREDIT", appLabel.valueUtf8());

        // Verify visitor traversal
        List<String> visited = new ArrayList<>();
        decodedRoot.accept(new TlvVisitor() {
            @Override
            public void enterConstructed(ConstructedTlv node, int depth) {
                visited.add("C:" + depth + ":" + node.tag().toHex());
            }

            @Override
            public void visitPrimitive(PrimitiveTlv node, int depth) {
                visited.add("P:" + depth + ":" + node.tag().toHex());
            }
        });
        assertEquals(List.of("C:0:6F", "P:1:84", "C:1:A5", "P:2:50", "P:2:9F26"), visited);

        // Verify hex dump formatting
        String dump = TlvHexFormatter.formatDump(decodedRoot);
        assertTrue(dump.contains("6F [APPLICATION,C]"));
        assertTrue(dump.contains("  84 [CONTEXT_SPECIFIC,P] len=7 val=A0000000031010"));
        assertTrue(dump.contains("    9F26 [CONTEXT_SPECIFIC,P] len=8 val=0102030405060708"));
    }

    @Test
    @DisplayName("Definite long-form length (0x81 and 0x82) encodes and decodes accurately")
    void definiteLongFormLengths() {
        byte[] payload200 = new byte[200];
        for (int i = 0; i < payload200.length; i++) {
            payload200[i] = (byte) (i & 0xFF);
        }
        PrimitiveTlv p200 = PrimitiveTlv.of(TlvTag.of(TagClass.UNIVERSAL, false, 4), payload200);
        byte[] enc200 = codec.encode(p200);
        assertEquals((byte) 0x04, enc200[0]);
        assertEquals((byte) 0x81, enc200[1]);
        assertEquals((byte) 200, enc200[2]);
        assertEquals(p200, codec.decode(enc200));

        byte[] payload300 = new byte[300];
        PrimitiveTlv p300 = PrimitiveTlv.of(TlvTag.of(TagClass.UNIVERSAL, false, 4), payload300);
        byte[] enc300 = codec.encode(p300);
        assertEquals((byte) 0x82, enc300[1]);
        assertEquals((byte) 0x01, enc300[2]);
        assertEquals((byte) 0x2C, enc300[3]);
        assertEquals(p300, codec.decode(enc300));
    }

    @Test
    @DisplayName("Indefinite-length constructed BER-TLV (0x80 ... 0x00 0x00) decodes recursively")
    void indefiniteLengthDecoding() {
        ConstructedTlv tree = TlvBuilder.constructedHex("30")
                .primitiveHex("02", "01")
                .primitiveHex("04", "DEADBEEF")
                .build();

        byte[] indefiniteBytes = codec.encodeIndefinite(tree);
        assertEquals((byte) 0x30, indefiniteBytes[0]);
        assertEquals((byte) 0x80, indefiniteBytes[1]);
        assertEquals((byte) 0x00, indefiniteBytes[indefiniteBytes.length - 2]);
        assertEquals((byte) 0x00, indefiniteBytes[indefiniteBytes.length - 1]);

        TlvNode decoded = codec.decode(indefiniteBytes);
        assertEquals(tree, decoded);
    }

    @Test
    @DisplayName("Streaming decodeAll reads multiple consecutive TLV nodes")
    void streamingDecodeAll() throws Exception {
        byte[] stream = TlvHexFormatter.fromHex("84 02 1234 9F26 03 AABBCC");
        List<TlvNode> nodes = codec.decodeAll(new ByteArrayInputStream(stream));
        assertEquals(2, nodes.size());
        assertEquals("84", nodes.get(0).tag().toHex());
        assertEquals("9F26", nodes.get(1).tag().toHex());
    }

    @Test
    @DisplayName("Malformed BER-TLV inputs, indefinite primitive tags, and limit violations throw TlvCodecException")
    void rejectsMalformedStreamsAndLimits() {
        // Indefinite length on primitive tag 0x04
        byte[] invalidIndefPrimitive = TlvHexFormatter.fromHex("04 80 01 02 00 00");
        assertThrows(TlvCodecException.class, () -> codec.decode(invalidIndefPrimitive));

        // Truncated value
        byte[] truncated = TlvHexFormatter.fromHex("04 05 AABB");
        assertThrows(TlvCodecException.class, () -> codec.decode(truncated));

        // Missing EOC in indefinite length
        byte[] missingEoc = TlvHexFormatter.fromHex("30 80 02 01 05");
        assertThrows(TlvCodecException.class, () -> codec.decode(missingEoc));

        // Max value length exceeded
        BerTlvCodec smallCodec = new BerTlvCodec(2, 4);
        byte[] tooLarge = TlvHexFormatter.fromHex("04 05 0102030405");
        assertThrows(TlvCodecException.class, () -> smallCodec.decode(tooLarge));

        // Max depth exceeded
        ConstructedTlv deep = TlvBuilder.constructedHex("30")
                .nestedHex("30", b1 -> b1.nestedHex("30", b2 -> b2.primitiveHex("02", "01")))
                .build();
        byte[] deepBytes = codec.encode(deep);
        assertThrows(TlvCodecException.class, () -> smallCodec.decode(deepBytes));
    }
}
