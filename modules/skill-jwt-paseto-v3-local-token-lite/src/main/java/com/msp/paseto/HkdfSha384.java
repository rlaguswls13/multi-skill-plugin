package com.msp.paseto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Minimal RFC 5869 HKDF-SHA384 implementation for PASETO v3 key splitting.
 */
final class HkdfSha384 {

    private static final String HMAC_SHA384 = "HmacSHA384";
    private static final int HASH_LEN = 48;

    private HkdfSha384() {
    }

    static byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length) {
        if (length <= 0 || length > 255 * HASH_LEN) {
            throw new IllegalArgumentException("Invalid HKDF output length: " + length);
        }
        byte[] effectiveSalt = (salt == null || salt.length == 0) ? new byte[HASH_LEN] : salt;
        byte[] prk = hmacSha384(effectiveSalt, ikm);
        try {
            byte[] okm = new byte[length];
            byte[] t = new byte[0];
            int copied = 0;
            int counter = 1;
            while (copied < length) {
                Mac mac = Mac.getInstance(HMAC_SHA384);
                mac.init(new SecretKeySpec(prk, HMAC_SHA384));
                mac.update(t);
                if (info != null && info.length > 0) {
                    mac.update(info);
                }
                mac.update((byte) counter);
                t = mac.doFinal();
                int toCopy = Math.min(t.length, length - copied);
                System.arraycopy(t, 0, okm, copied, toCopy);
                copied += toCopy;
                counter++;
            }
            Arrays.fill(t, (byte) 0);
            return okm;
        } catch (GeneralSecurityException e) {
            throw new PasetoException("HKDF-SHA384 expansion failed", e);
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
    }

    static byte[] hmacSha384(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA384);
            mac.init(new SecretKeySpec(key, HMAC_SHA384));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new PasetoException("HMAC-SHA384 computation failed", e);
        }
    }
}
