package com.msp.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    @Test
    @DisplayName("SHA-256 standard vectors")
    void testSha256Vectors() {
        // Empty string SHA-256
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtil.sha256Hex(""));

        // "hello world" SHA-256
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                HashUtil.sha256Hex("hello world"));
    }

    @Test
    @DisplayName("SHA-512 standard vectors")
    void testSha512Vectors() {
        // Empty string SHA-512
        assertEquals("cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
                HashUtil.sha512Hex(""));

        // "hello world" SHA-512
        assertEquals("309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f",
                HashUtil.sha512Hex("hello world"));
    }

    @Test
    @DisplayName("Hex conversions roundtrip")
    void testHexConversions() {
        byte[] bytes = new byte[]{(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef, 0x01, 0x23};
        String hex = HashUtil.bytesToHex(bytes);
        assertEquals("deadbeef0123", hex);

        byte[] parsed = HashUtil.hexToBytes(hex);
        assertArrayEquals(bytes, parsed);
    }
}
