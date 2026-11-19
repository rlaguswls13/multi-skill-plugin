package com.msp.starter.stringcodec;

import com.msp.ahocorasick.AhoCorasick;
import com.msp.ahocorasick.PatternMatch;
import com.msp.kmp.KmpAutomaton;
import com.msp.suffixarray.SuffixArray;
import com.msp.tlvcodec.ConstructedTlv;
import com.msp.tlvcodec.PrimitiveTlv;
import com.msp.tlvcodec.TlvNode;
import com.msp.tlvcodec.TlvTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringCodecKitTest {

    @Test
    @DisplayName("1. Aho-Corasick multi-pattern matching")
    void testAhoCorasick() {
        List<String> keywords = List.of("he", "she", "his", "hers");
        AhoCorasick ac = StringCodecKit.ahoCorasick("he", "she", "his", "hers");
        assertEquals(4, ac.patternCount());

        List<PatternMatch> matches = StringCodecKit.findAllPatterns("ushers", keywords);
        List<String> matchedWords = matches.stream().map(PatternMatch::keyword).toList();
        assertEquals(List.of("she", "he", "hers"), matchedWords);

        assertTrue(StringCodecKit.containsAnyPattern("hello world", keywords));
        assertFalse(StringCodecKit.containsAnyPattern("xyz", keywords));
    }

    @Test
    @DisplayName("2. KMP automaton zero-backtracking search")
    void testKmp() {
        assertEquals(2, StringCodecKit.kmpIndexOf("ababcababd", "abc"));
        assertEquals(-1, StringCodecKit.kmpIndexOf("abcdef", "xyz"));
        assertEquals(List.of(0, 2, 4), StringCodecKit.kmpFindAll("abababa", "aba"));

        KmpAutomaton compiled = StringCodecKit.kmp("needle");
        assertEquals(4, compiled.indexOf("hay needle stack"));
    }

    @Test
    @DisplayName("3. Suffix Array and Kasai LCP operations")
    void testSuffixArray() {
        SuffixArray sa = StringCodecKit.suffixArray("banana");
        assertEquals(2, sa.countOccurrences("ana"));
        assertEquals(List.of(1, 3), sa.findOccurrences("ana"));
        assertEquals(15L, sa.countDistinctSubstrings());

        assertEquals("ana", StringCodecKit.longestRepeatedSubstring("banana"));
        assertEquals("bcde", StringCodecKit.longestCommonSubstring("xabcdey", "zbcdew"));
    }

    @Test
    @DisplayName("4. VarInt / ZigZag / Signed LEB128 round-trip encoding")
    void testVarIntLeb128() {
        byte[] uBytes = StringCodecKit.encodeUnsignedVarInt(300L);
        assertArrayEquals(new byte[]{(byte) 0xAC, 0x02}, uBytes);
        assertEquals(300L, StringCodecKit.decodeUnsignedVarInt(uBytes));

        byte[] zzBytes = StringCodecKit.encodeZigZagVarInt(-42L);
        assertEquals(1, zzBytes.length);
        assertEquals(-42L, StringCodecKit.decodeZigZagVarInt(zzBytes));

        byte[] sLebBytes = StringCodecKit.encodeSignedLeb128(-123456L);
        assertEquals(-123456L, StringCodecKit.decodeSignedLeb128(sLebBytes));
    }

    @Test
    @DisplayName("5. z-base-32 human-oriented encoding and validation")
    void testZBase32() {
        byte[] raw = "Hello MSP!".getBytes(StandardCharsets.UTF_8);
        String encoded = StringCodecKit.encodeZBase32(raw);

        assertFalse(encoded.isEmpty());
        assertTrue(StringCodecKit.isValidZBase32(encoded));
        assertArrayEquals(raw, StringCodecKit.decodeZBase32(encoded));
        assertFalse(StringCodecKit.isValidZBase32("invalid=padding!"));
    }

    @Test
    @DisplayName("6. ASN.1 BER-TLV / EMV TLV tree encoding and decoding")
    void testBerTlv() {
        ConstructedTlv tree = StringCodecKit.tlvBuilderHex("6F")
                .primitiveHex("84", "A0000000031010")
                .nestedHex("A5", sub -> sub.primitiveUtf8(TlvTag.fromHex("50"), "VISA"))
                .build();

        byte[] wire = StringCodecKit.encodeBerTlv(tree);
        TlvNode decoded = StringCodecKit.decodeBerTlv(wire);

        assertInstanceOf(ConstructedTlv.class, decoded);
        ConstructedTlv root = (ConstructedTlv) decoded;
        assertEquals(tree, root);

        TlvNode labelNode = root.findFirst(TlvTag.fromHex("50")).orElseThrow();
        assertInstanceOf(PrimitiveTlv.class, labelNode);
        assertEquals("VISA", ((PrimitiveTlv) labelNode).valueUtf8());

        List<TlvNode> streamNodes = StringCodecKit.decodeAllBerTlv(wire);
        assertEquals(1, streamNodes.size());
    }
}
