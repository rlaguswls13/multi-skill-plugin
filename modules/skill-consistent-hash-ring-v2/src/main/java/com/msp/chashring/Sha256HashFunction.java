package com.msp.chashring;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Default SHA-256 based 64-bit hash function providing uniform hash distribution.
 */
public final class Sha256HashFunction implements HashFunction {

    public static final Sha256HashFunction INSTANCE = new Sha256HashFunction();

    @Override
    public long hash(String key) {
        if (key == null) {
            return 0L;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in current JVM", e);
        }
    }
}
