package com.msp.paseto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class PasetoV3LocalTest {

    @Test
    void encryptAndDecryptRoundTripWithFooterAndImplicitAssertion() {
        byte[] key = PasetoV3Local.generateKey();
        String payload = "{\"sub\":\"user-42\",\"role\":\"admin\",\"exp\":\"2030-01-01T00:00:00Z\"}";
        String footer = "{\"kid\":\"k3.lid.v1\"}";
        byte[] implicit = "tenant:acme-corp".getBytes(StandardCharsets.UTF_8);

        String token = PasetoV3Local.encryptString(key, payload, footer, "tenant:acme-corp");
        assertTrue(token.startsWith("v3.local."));
        assertEquals(footer, new String(PasetoV3Local.extractUnverifiedFooter(token), StandardCharsets.UTF_8));

        PasetoV3Token decrypted = PasetoV3Local.decrypt(key, token, footer.getBytes(StandardCharsets.UTF_8), implicit);
        assertEquals(payload, decrypted.payloadAsUtf8());
        assertEquals(footer, decrypted.footerAsUtf8());
        assertTrue(decrypted.hasFooter());
    }

    @Test
    void encryptAndDecryptEmptyPlaintextWithoutFooter() {
        byte[] key = PasetoV3Local.generateKey();
        String token = PasetoV3Local.encrypt(key, new byte[0]);
        assertTrue(token.startsWith("v3.local."));
        assertFalse(token.substring("v3.local.".length()).contains("."));

        PasetoV3Token decrypted = PasetoV3Local.decrypt(key, token);
        assertEquals(0, decrypted.payload().length);
        assertFalse(decrypted.hasFooter());
    }

    @Test
    void deterministicEncryptWithFixedNonceMatchesExpectedLayout() {
        // Fixed key & zero nonce, empty message, empty footer, empty implicit
        byte[] key = HexFormat.of().parseHex("707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f");
        byte[] nonce = new byte[32];
        byte[] plaintext = new byte[0];

        String token1 = PasetoV3Local.encryptWithNonce(key, plaintext, new byte[0], new byte[0], nonce);
        String token2 = PasetoV3Local.encryptWithNonce(key, plaintext, new byte[0], new byte[0], nonce);
        assertEquals(token1, token2);
        // 9 ("v3.local.") + base64url_nopad(32 nonce + 0 ct + 48 tag = 80 bytes -> 107 chars) = 116
        assertEquals(116, token1.length());
        assertTrue(token1.startsWith("v3.local.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));

        PasetoV3Token result = PasetoV3Local.decrypt(key, token1);
        assertArrayEquals(plaintext, result.payload());
    }

    @Test
    void tamperPayloadFooterImplicitOrWrongKeyFailsAuthentication() {
        byte[] key = PasetoV3Local.generateKey();
        byte[] wrongKey = PasetoV3Local.generateKey();
        String token = PasetoV3Local.encryptString(key, "{\"msg\":\"secret\"}", "{\"kid\":\"1\"}", "ctx-a");

        // Wrong key
        assertThrows(PasetoException.class, () ->
                PasetoV3Local.decrypt(wrongKey, token, "ctx-a".getBytes(StandardCharsets.UTF_8)));

        // Wrong implicit assertion
        assertThrows(PasetoException.class, () ->
                PasetoV3Local.decrypt(key, token, "ctx-b".getBytes(StandardCharsets.UTF_8)));

        // Tamper first byte inside decoded payload (safe across Base64url alignment)
        String remainder = token.substring("v3.local.".length());
        int dot = remainder.indexOf('.');
        String b64Payload = remainder.substring(0, dot);
        String b64Footer = remainder.substring(dot + 1);
        byte[] rawPayload = Base64.getUrlDecoder().decode(b64Payload);
        rawPayload[0] ^= 0x01; // flip bit in nonce
        String tamperedNonceToken = "v3.local." + Base64.getUrlEncoder().withoutPadding().encodeToString(rawPayload) + "." + b64Footer;
        assertThrows(PasetoException.class, () ->
                PasetoV3Local.decrypt(key, tamperedNonceToken, "ctx-a".getBytes(StandardCharsets.UTF_8)));

        // Flip bit in authentication tag (last byte)
        byte[] rawPayload2 = Base64.getUrlDecoder().decode(b64Payload);
        rawPayload2[rawPayload2.length - 1] ^= 0x80;
        String tamperedTagToken = "v3.local." + Base64.getUrlEncoder().withoutPadding().encodeToString(rawPayload2) + "." + b64Footer;
        assertThrows(PasetoException.class, () ->
                PasetoV3Local.decrypt(key, tamperedTagToken, "ctx-a".getBytes(StandardCharsets.UTF_8)));

        // Tamper footer
        String tamperedFooterToken = "v3.local." + b64Payload + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"kid\":\"2\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(PasetoException.class, () ->
                PasetoV3Local.decrypt(key, tamperedFooterToken, "ctx-a".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void strictHeaderAndFormatValidation() {
        byte[] key = PasetoV3Local.generateKey();
        assertThrows(IllegalArgumentException.class, () -> PasetoV3Local.encrypt(new byte[16], new byte[1]));
        assertThrows(PasetoException.class, () -> PasetoV3Local.decrypt(key, "v4.local.abc"));
        assertThrows(PasetoException.class, () -> PasetoV3Local.decrypt(key, "v3.public.abc"));
        assertThrows(PasetoException.class, () -> PasetoV3Local.decrypt(key, "v3.local."));
        assertThrows(PasetoException.class, () -> PasetoV3Local.decrypt(key, "v3.local.AAAA=="));
        assertThrows(PasetoException.class, () -> PasetoV3Local.decrypt(key, "v3.local.AAAA.BBBB.CCCC"));
    }

    @Test
    void paeEncodingMatchesSpecification() {
        // PAE([]) = 0x0000000000000000
        assertEquals("0000000000000000", HexFormat.of().formatHex(PaeEncoder.encode()));
        // PAE(["test"]) = 0100000000000000 || 0400000000000000 || 74657374
        byte[] encoded = PaeEncoder.encode("test".getBytes(StandardCharsets.UTF_8));
        assertEquals("0100000000000000040000000000000074657374", HexFormat.of().formatHex(encoded));
    }
}
