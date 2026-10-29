package com.msp.idgen;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Cryptographically secure random URL-safe ID generator (NanoID implementation).
 *
 * <p>Uses bitmasking to eliminate modulo bias for completely uniform entropy distribution.
 * Default alphabet contains 64 URL-safe characters: {@code _~0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ}.
 * Default ID size is 21 characters (~149 billion IDs needed for a 1% collision probability).
 */
public final class NanoIdGenerator {

    /** Standard URL-safe 64-character NanoID alphabet. */
    public static final char[] DEFAULT_ALPHABET =
            "_~0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /** Default length of a generated NanoID. */
    public static final int DEFAULT_SIZE = 21;

    private static final SecureRandom DEFAULT_RANDOM = new SecureRandom();

    private final SecureRandom random;
    private final char[] alphabet;
    private final int defaultSize;

    public NanoIdGenerator() {
        this(DEFAULT_RANDOM, DEFAULT_ALPHABET, DEFAULT_SIZE);
    }

    public NanoIdGenerator(int defaultSize) {
        this(DEFAULT_RANDOM, DEFAULT_ALPHABET, defaultSize);
    }

    public NanoIdGenerator(char[] alphabet, int defaultSize) {
        this(DEFAULT_RANDOM, alphabet, defaultSize);
    }

    public NanoIdGenerator(SecureRandom random, char[] alphabet, int defaultSize) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.alphabet = Objects.requireNonNull(alphabet, "alphabet must not be null").clone();
        if (this.alphabet.length == 0 || this.alphabet.length >= 256) {
            throw new IllegalArgumentException("Alphabet size must be between 1 and 255: " + this.alphabet.length);
        }
        if (defaultSize <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + defaultSize);
        }
        this.defaultSize = defaultSize;
    }

    /**
     * Generates a 21-character NanoID using the default alphabet and SecureRandom.
     */
    public static String randomNanoId() {
        return randomNanoId(DEFAULT_RANDOM, DEFAULT_ALPHABET, DEFAULT_SIZE);
    }

    /**
     * Generates a NanoID with the specified length using the default alphabet.
     */
    public static String randomNanoId(int size) {
        return randomNanoId(DEFAULT_RANDOM, DEFAULT_ALPHABET, size);
    }

    /**
     * Generates a NanoID with custom SecureRandom, alphabet, and size.
     */
    public static String randomNanoId(SecureRandom random, char[] alphabet, int size) {
        Objects.requireNonNull(random, "random must not be null");
        Objects.requireNonNull(alphabet, "alphabet must not be null");

        if (alphabet.length == 0 || alphabet.length >= 256) {
            throw new IllegalArgumentException("Alphabet size must be between 1 and 255: " + alphabet.length);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }

        // Calculate bitmask for closest power of 2
        int mask = (2 << (int) Math.floor(Math.log(alphabet.length - 1) / Math.log(2))) - 1;
        // Step size optimal buffer allocation
        int step = (int) Math.ceil(1.6 * mask * size / alphabet.length);

        StringBuilder idBuilder = new StringBuilder(size);
        byte[] bytes = new byte[step];

        while (true) {
            random.nextBytes(bytes);
            for (int i = 0; i < step; i++) {
                int alphabetIndex = bytes[i] & mask;
                if (alphabetIndex < alphabet.length) {
                    idBuilder.append(alphabet[alphabetIndex]);
                    if (idBuilder.length() == size) {
                        return idBuilder.toString();
                    }
                }
            }
        }
    }

    /**
     * Generates an ID using the instance's configured settings.
     */
    public String generate() {
        return randomNanoId(random, alphabet, defaultSize);
    }

    /**
     * Generates an ID with the instance's alphabet and random, but custom size.
     */
    public String generate(int size) {
        return randomNanoId(random, alphabet, size);
    }
}
