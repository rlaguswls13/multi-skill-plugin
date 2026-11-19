package com.msp.starter.stringcodec;

import com.msp.ahocorasick.AhoCorasick;
import com.msp.ahocorasick.PatternMatch;
import com.msp.kmp.KmpAutomaton;
import com.msp.suffixarray.SuffixArray;
import com.msp.tlvcodec.BerTlvCodec;
import com.msp.tlvcodec.TlvBuilder;
import com.msp.tlvcodec.TlvNode;
import com.msp.varint.VarIntLeb128Codec;
import com.msp.zbase32.ZBase32Codec;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Multi-Pattern String Search, Suffix Array/LCP, and Binary Wire Codec Starter Facade.
 *
 * <p>Unifies six zero-dependency Java 21 modules behind a single entry point:
 * <ul>
 *   <li>{@link AhoCorasick} — O(N + M) multi-pattern Trie search automaton</li>
 *   <li>{@link KmpAutomaton} — O(N) zero-backtracking Knuth-Morris-Pratt search</li>
 *   <li>{@link SuffixArray} — O(N log N) suffix array &amp; Kasai LCP engine</li>
 *   <li>{@link VarIntLeb128Codec} — Protocol Buffers VarInt, ZigZag, and Signed LEB128 codec</li>
 *   <li>{@link ZBase32Codec} — human-oriented z-base-32 bit/byte codec</li>
 *   <li>{@link BerTlvCodec} — streaming ASN.1 BER-TLV / EMV TLV encoder &amp; decoder</li>
 * </ul>
 */
public final class StringCodecKit {

    private static final BerTlvCodec DEFAULT_BER_TLV_CODEC = new BerTlvCodec();

    private StringCodecKit() {
    }

    // -------------------------------------------------------------------------
    // 1. Aho-Corasick Multi-Pattern Search
    // -------------------------------------------------------------------------

    /**
     * Compiles an immutable {@link AhoCorasick} automaton from a collection of keywords.
     */
    public static AhoCorasick ahoCorasick(Collection<String> keywords) {
        return AhoCorasick.from(keywords);
    }

    /**
     * Compiles an immutable {@link AhoCorasick} automaton from varargs keywords.
     */
    public static AhoCorasick ahoCorasick(String... keywords) {
        return AhoCorasick.from(Arrays.asList(keywords));
    }

    /**
     * One-shot multi-pattern search returning all (including overlapping) matches.
     */
    public static List<PatternMatch> findAllPatterns(CharSequence text, Collection<String> keywords) {
        return AhoCorasick.from(keywords).findAll(text);
    }

    /**
     * Returns {@code true} if {@code text} contains any of the given {@code keywords}.
     */
    public static boolean containsAnyPattern(CharSequence text, Collection<String> keywords) {
        return AhoCorasick.from(keywords).containsAny(text);
    }

    // -------------------------------------------------------------------------
    // 2. Knuth-Morris-Pratt (KMP) Automaton
    // -------------------------------------------------------------------------

    /**
     * Compiles a reusable {@link KmpAutomaton} for {@code pattern}.
     */
    public static KmpAutomaton kmp(CharSequence pattern) {
        return KmpAutomaton.compile(pattern);
    }

    /**
     * Returns the first starting index of {@code pattern} in {@code text}, or {@code -1} if absent.
     */
    public static int kmpIndexOf(CharSequence text, CharSequence pattern) {
        return KmpAutomaton.compile(pattern).indexOf(text);
    }

    /**
     * Finds all starting indices of {@code pattern} in {@code text} (including overlapping matches).
     */
    public static List<Integer> kmpFindAll(CharSequence text, CharSequence pattern) {
        return KmpAutomaton.compile(pattern).findAll(text);
    }

    // -------------------------------------------------------------------------
    // 3. Suffix Array & Kasai LCP
    // -------------------------------------------------------------------------

    /**
     * Builds an immutable {@link SuffixArray} and LCP array for {@code text}.
     */
    public static SuffixArray suffixArray(String text) {
        return SuffixArray.build(text);
    }

    /**
     * Computes a longest common substring shared by {@code first} and {@code second}.
     */
    public static String longestCommonSubstring(String first, String second) {
        return SuffixArray.longestCommonSubstring(first, second);
    }

    /**
     * Computes the lexicographically smallest longest repeated substring within {@code text}.
     */
    public static String longestRepeatedSubstring(String text) {
        return SuffixArray.build(text).longestRepeatedSubstring();
    }

    // -------------------------------------------------------------------------
    // 4. VarInt / ZigZag / Signed LEB128 Codec
    // -------------------------------------------------------------------------

    /**
     * Encodes a 64-bit unsigned integer as Protocol Buffers VarInt / ULEB128 bytes.
     */
    public static byte[] encodeUnsignedVarInt(long value) {
        return VarIntLeb128Codec.writeUnsignedVarInt(value);
    }

    /**
     * Decodes an unsigned 64-bit VarInt / ULEB128 from {@code bytes}.
     */
    public static long decodeUnsignedVarInt(byte[] bytes) {
        return VarIntLeb128Codec.readUnsignedVarInt(bytes);
    }

    /**
     * Encodes a signed 64-bit integer using ZigZag + VarInt encoding (Protobuf {@code sint64}).
     */
    public static byte[] encodeZigZagVarInt(long signedValue) {
        return VarIntLeb128Codec.writeZigZagVarInt(signedValue);
    }

    /**
     * Decodes a ZigZag-encoded signed 64-bit VarInt from {@code bytes}.
     */
    public static long decodeZigZagVarInt(byte[] bytes) {
        return VarIntLeb128Codec.readZigZagVarInt(bytes);
    }

    /**
     * Encodes a signed 64-bit integer using two's complement Signed LEB128 (DWARF / WebAssembly).
     */
    public static byte[] encodeSignedLeb128(long value) {
        return VarIntLeb128Codec.writeSignedLeb128(value);
    }

    /**
     * Decodes a two's complement Signed LEB128 64-bit integer from {@code bytes}.
     */
    public static long decodeSignedLeb128(byte[] bytes) {
        return VarIntLeb128Codec.readSignedLeb128(bytes);
    }

    // -------------------------------------------------------------------------
    // 5. z-base-32 Human-Oriented Codec
    // -------------------------------------------------------------------------

    /**
     * Encodes raw bytes into a human-oriented z-base-32 string (no padding).
     */
    public static String encodeZBase32(byte[] data) {
        return ZBase32Codec.encodeBytes(data);
    }

    /**
     * Strictly decodes a z-base-32 string back into raw bytes.
     */
    public static byte[] decodeZBase32(String encoded) {
        return ZBase32Codec.decodeBytes(encoded);
    }

    /**
     * Checks whether {@code encoded} is a valid canonical z-base-32 string.
     */
    public static boolean isValidZBase32(String encoded) {
        return ZBase32Codec.strict().isValid(encoded);
    }

    // -------------------------------------------------------------------------
    // 6. ASN.1 BER-TLV / EMV TLV Codec
    // -------------------------------------------------------------------------

    /**
     * Returns the shared thread-safe default {@link BerTlvCodec}.
     */
    public static BerTlvCodec berTlvCodec() {
        return DEFAULT_BER_TLV_CODEC;
    }

    /**
     * Encodes a {@link TlvNode} using definite-length BER-TLV encoding.
     */
    public static byte[] encodeBerTlv(TlvNode node) {
        return DEFAULT_BER_TLV_CODEC.encode(node);
    }

    /**
     * Decodes a single {@link TlvNode} from BER-TLV bytes.
     */
    public static TlvNode decodeBerTlv(byte[] data) {
        return DEFAULT_BER_TLV_CODEC.decode(data);
    }

    /**
     * Decodes all consecutive {@link TlvNode} elements from BER-TLV bytes.
     */
    public static List<TlvNode> decodeAllBerTlv(byte[] data) {
        return DEFAULT_BER_TLV_CODEC.decodeAll(data);
    }

    /**
     * Starts a fluent {@link TlvBuilder} for a constructed tag specified in hexadecimal (e.g. {@code "6F"} or {@code "30"}).
     */
    public static TlvBuilder tlvBuilderHex(String constructedTagHex) {
        return TlvBuilder.constructedHex(constructedTagHex);
    }
}
