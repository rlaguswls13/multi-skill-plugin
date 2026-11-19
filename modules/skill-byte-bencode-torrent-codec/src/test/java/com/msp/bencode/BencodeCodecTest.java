package com.msp.bencode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BencodeCodecTest {

    @Test
    @DisplayName("Integer encoding and decoding (BEP 0003)")
    void testIntegers() {
        assertEquals(42L, ((BencodeInteger) Bencode.decode("i42e".getBytes(StandardCharsets.US_ASCII))).value());
        assertEquals(-3L, ((BencodeInteger) Bencode.decode("i-3e".getBytes(StandardCharsets.US_ASCII))).value());
        assertEquals(0L, ((BencodeInteger) Bencode.decode("i0e".getBytes(StandardCharsets.US_ASCII))).value());

        // Roundtrip serialization
        assertArrayEquals("i42e".getBytes(StandardCharsets.US_ASCII), Bencode.encode(new BencodeInteger(42)));
        assertArrayEquals("i-3e".getBytes(StandardCharsets.US_ASCII), Bencode.encode(new BencodeInteger(-3)));
        assertArrayEquals("i0e".getBytes(StandardCharsets.US_ASCII), Bencode.encode(new BencodeInteger(0)));
    }

    @Test
    @DisplayName("Invalid integers must be rejected according to BEP 0003")
    void testInvalidIntegers() {
        // Leading zeros forbidden
        assertThrows(BencodeException.class, () -> Bencode.decode("i03e".getBytes(StandardCharsets.US_ASCII)));
        // Negative zero forbidden
        assertThrows(BencodeException.class, () -> Bencode.decode("i-0e".getBytes(StandardCharsets.US_ASCII)));
        // Empty integer forbidden
        assertThrows(BencodeException.class, () -> Bencode.decode("ie".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(BencodeException.class, () -> Bencode.decode("i-e".getBytes(StandardCharsets.US_ASCII)));
        // Non-digit characters
        assertThrows(BencodeException.class, () -> Bencode.decode("i12a3e".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("String encoding and decoding (BEP 0003)")
    void testStrings() {
        BencodeString spam = (BencodeString) Bencode.decode("4:spam".getBytes(StandardCharsets.US_ASCII));
        assertEquals("spam", spam.asString());
        assertEquals(4, spam.length());
        assertArrayEquals("4:spam".getBytes(StandardCharsets.US_ASCII), Bencode.encode(spam));

        // Empty string
        BencodeString empty = (BencodeString) Bencode.decode("0:".getBytes(StandardCharsets.US_ASCII));
        assertEquals("", empty.asString());
        assertEquals(0, empty.length());
        assertArrayEquals("0:".getBytes(StandardCharsets.US_ASCII), Bencode.encode(empty));

        // Binary bytes containing non-ASCII / zeroes
        byte[] rawBytes = new byte[]{0x00, (byte) 0xFF, 0x42, (byte) 0x80};
        byte[] encoded = Bencode.encode(new BencodeString(rawBytes));
        assertEquals("4:", new String(encoded, 0, 2, StandardCharsets.US_ASCII));
        BencodeString decoded = (BencodeString) Bencode.decode(encoded);
        assertArrayEquals(rawBytes, decoded.getBytes());
    }

    @Test
    @DisplayName("Invalid strings must be rejected")
    void testInvalidStrings() {
        // Leading zero in string length
        assertThrows(BencodeException.class, () -> Bencode.decode("04:spam".getBytes(StandardCharsets.US_ASCII)));
        // Truncated string
        assertThrows(BencodeException.class, () -> Bencode.decode("10:short".getBytes(StandardCharsets.US_ASCII)));
        // Trailing bytes after root value
        assertThrows(BencodeException.class, () -> Bencode.decode("4:spamtrailing".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("List encoding and decoding")
    void testLists() {
        byte[] bencoded = "l4:spami42ee".getBytes(StandardCharsets.US_ASCII);
        BencodeList list = (BencodeList) Bencode.decode(bencoded);
        assertEquals(2, list.size());
        assertEquals("spam", ((BencodeString) list.get(0)).asString());
        assertEquals(42L, ((BencodeInteger) list.get(1)).value());

        assertArrayEquals(bencoded, Bencode.encode(list));

        // Empty list
        BencodeList empty = (BencodeList) Bencode.decode("le".getBytes(StandardCharsets.US_ASCII));
        assertTrue(empty.isEmpty());
        assertArrayEquals("le".getBytes(StandardCharsets.US_ASCII), Bencode.encode(empty));
    }

    @Test
    @DisplayName("Dictionary encoding and decoding with strict sorting")
    void testDictionaries() {
        byte[] bencoded = "d3:bar4:spam3:fooi42ee".getBytes(StandardCharsets.US_ASCII);
        BencodeDict dict = Bencode.decodeDict(bencoded);
        assertEquals(2, dict.size());
        assertEquals("spam", dict.getString("bar").orElseThrow());
        assertEquals(42L, dict.getInt("foo").orElseThrow());

        assertArrayEquals(bencoded, Bencode.encode(dict));

        // Empty dictionary
        BencodeDict empty = (BencodeDict) Bencode.decode("de".getBytes(StandardCharsets.US_ASCII));
        assertTrue(empty.isEmpty());
        assertArrayEquals("de".getBytes(StandardCharsets.US_ASCII), Bencode.encode(empty));
    }

    @Test
    @DisplayName("Dictionary with unsorted or duplicate keys must be rejected")
    void testInvalidDictionaries() {
        // "foo" before "bar" is out of order
        byte[] unsorted = "d3:foo4:spam3:bari42ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(BencodeException.class, () -> Bencode.decode(unsorted));

        // Duplicate key "foo"
        byte[] duplicate = "d3:fooi1e3:fooi2ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(BencodeException.class, () -> Bencode.decode(duplicate));
    }

    @Test
    @DisplayName("Authentic Torrent metainfo round-trip test")
    void testTorrentMetainfo() {
        byte[] fakePieces = new byte[40]; // 2 sha1 hashes of 20 bytes each
        for (int i = 0; i < 40; i++) fakePieces[i] = (byte) (i & 0xFF);

        BencodeDict infoDict = BencodeDict.builder()
                .put("length", 10485760L)
                .put("name", "debian-12.0.0-amd64-netinst.iso")
                .put("piece length", 262144L)
                .put("pieces", fakePieces)
                .build();

        BencodeDict torrent = BencodeDict.builder()
                .put("announce", "http://bttracker.debian.org:6969/announce")
                .put("comment", "\"Debian CD from cdimage.debian.org\"")
                .put("created by", "mktorrent 1.1")
                .put("creation date", 1686411540L)
                .put("info", infoDict)
                .build();

        byte[] encoded = Bencode.encode(torrent);
        assertNotNull(encoded);
        assertTrue(encoded.length > 50);

        BencodeDict decoded = Bencode.decodeDict(encoded);
        assertEquals("http://bttracker.debian.org:6969/announce", decoded.getString("announce").orElseThrow());
        assertEquals(1686411540L, decoded.getInt("creation date").orElseThrow());

        BencodeDict decodedInfo = decoded.getDict("info").orElseThrow();
        assertEquals("debian-12.0.0-amd64-netinst.iso", decodedInfo.getString("name").orElseThrow());
        assertEquals(10485760L, decodedInfo.getInt("length").orElseThrow());
        assertArrayEquals(fakePieces, decodedInfo.getBytes("pieces").orElseThrow());

        // Byte-level idempotency
        byte[] reEncoded = Bencode.encode(decoded);
        assertArrayEquals(encoded, reEncoded);
    }

    @Test
    @DisplayName("Java Object serialization to Bencode")
    void testJavaObjectSerialization() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("z_key", 100);
        map.put("a_key", "hello");
        map.put("list", List.of("x", 1, 2));

        byte[] encoded = Bencode.encode(map);
        // Keys should be automatically sorted: a_key, list, z_key
        BencodeDict dict = Bencode.decodeDict(encoded);
        assertEquals("hello", dict.getString("a_key").orElseThrow());
        assertEquals(100L, dict.getInt("z_key").orElseThrow());
        assertEquals(3, dict.getList("list").orElseThrow().size());
    }
}
