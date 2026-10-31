package com.msp.bitmapcounter;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongConsumer;

/**
 * Thread-safe 64-bit Long array bitmap supporting fast set/get/clear operations,
 * atomic bitwise set operations (AND, OR, XOR, AND-NOT), and high-speed cardinality
 * (population count) calculations for DAU/MAU analytics.
 */
public class LongBitmapCounter {

    private static final int INITIAL_WORDS = 16; // 1024 bits initial capacity
    private static final int ADDRESS_BITS_PER_WORD = 6; // 2^6 = 64
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD; // 64
    private static final long BIT_INDEX_MASK = BITS_PER_WORD - 1; // 63

    private long[] words;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Creates an empty bitmap counter with default initial capacity.
     */
    public LongBitmapCounter() {
        this(INITIAL_WORDS * BITS_PER_WORD);
    }

    /**
     * Creates an empty bitmap counter with enough capacity to hold the given number of bits.
     *
     * @param initialBits initial bit capacity
     */
    public LongBitmapCounter(long initialBits) {
        if (initialBits < 0) {
            throw new IllegalArgumentException("initialBits cannot be negative: " + initialBits);
        }
        int numWords = wordIndex(Math.max(0, initialBits - 1)) + 1;
        this.words = new long[Math.max(numWords, 1)];
    }

    /**
     * Internal constructor with predefined words array.
     */
    private LongBitmapCounter(long[] words) {
        this.words = Objects.requireNonNull(words);
    }

    private static int wordIndex(long bitIndex) {
        return (int) (bitIndex >>> ADDRESS_BITS_PER_WORD);
    }

    /**
     * Sets the bit at the specified index to 1.
     * Expands internal storage dynamically if necessary.
     *
     * @param bitIndex non-negative bit index
     */
    public void set(long bitIndex) {
        checkIndex(bitIndex);
        int wIndex = wordIndex(bitIndex);
        long mask = 1L << (bitIndex & BIT_INDEX_MASK);

        rwLock.writeLock().lock();
        try {
            ensureCapacity(wIndex + 1);
            words[wIndex] |= mask;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Checks if the bit at the specified index is set to 1.
     *
     * @param bitIndex non-negative bit index
     * @return true if set (1), false otherwise
     */
    public boolean get(long bitIndex) {
        checkIndex(bitIndex);
        int wIndex = wordIndex(bitIndex);
        long mask = 1L << (bitIndex & BIT_INDEX_MASK);

        rwLock.readLock().lock();
        try {
            if (wIndex >= words.length) {
                return false;
            }
            return (words[wIndex] & mask) != 0L;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears the bit at the specified index (sets it to 0).
     *
     * @param bitIndex non-negative bit index
     */
    public void clear(long bitIndex) {
        checkIndex(bitIndex);
        int wIndex = wordIndex(bitIndex);
        long mask = ~(1L << (bitIndex & BIT_INDEX_MASK));

        rwLock.writeLock().lock();
        try {
            if (wIndex < words.length) {
                words[wIndex] &= mask;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Sets a continuous range of bits [fromIndex, toIndex) to 1.
     *
     * @param fromIndex inclusive start index
     * @param toIndex   exclusive end index
     */
    public void setRange(long fromIndex, long toIndex) {
        checkIndex(fromIndex);
        if (toIndex < fromIndex) {
            throw new IllegalArgumentException("toIndex cannot be less than fromIndex");
        }
        if (fromIndex == toIndex) {
            return;
        }

        int startWord = wordIndex(fromIndex);
        int endWord = wordIndex(toIndex - 1);

        rwLock.writeLock().lock();
        try {
            ensureCapacity(endWord + 1);

            long firstWordMask = -1L << (fromIndex & BIT_INDEX_MASK);
            long lastWordMask = -1L >>> (BITS_PER_WORD - (toIndex & BIT_INDEX_MASK));
            if ((toIndex & BIT_INDEX_MASK) == 0) {
                lastWordMask = -1L;
            }

            if (startWord == endWord) {
                words[startWord] |= (firstWordMask & lastWordMask);
            } else {
                words[startWord] |= firstWordMask;
                for (int i = startWord + 1; i < endWord; i++) {
                    words[i] = -1L;
                }
                words[endWord] |= lastWordMask;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Calculates the population count (total number of set bits) using Long.bitCount.
     *
     * @return number of bits set to 1
     */
    public long cardinality() {
        rwLock.readLock().lock();
        try {
            long count = 0L;
            for (long w : words) {
                count += Long.bitCount(w);
            }
            return count;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns true if no bits are set to 1.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        rwLock.readLock().lock();
        try {
            for (long w : words) {
                if (w != 0L) {
                    return false;
                }
            }
            return true;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the current total bit capacity allocated by this counter.
     *
     * @return number of bits physically allocated
     */
    public long sizeInBits() {
        rwLock.readLock().lock();
        try {
            return (long) words.length * BITS_PER_WORD;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns a new LongBitmapCounter representing the bitwise AND (intersection) of this and other.
     *
     * @param other other bitmap
     * @return new intersection bitmap
     */
    public LongBitmapCounter and(LongBitmapCounter other) {
        Objects.requireNonNull(other, "other bitmap must not be null");
        this.rwLock.readLock().lock();
        other.rwLock.readLock().lock();
        try {
            int len = Math.min(this.words.length, other.words.length);
            long[] resultWords = new long[len];
            for (int i = 0; i < len; i++) {
                resultWords[i] = this.words[i] & other.words[i];
            }
            return new LongBitmapCounter(resultWords);
        } finally {
            other.rwLock.readLock().unlock();
            this.rwLock.readLock().unlock();
        }
    }

    /**
     * Returns a new LongBitmapCounter representing the bitwise OR (union) of this and other.
     *
     * @param other other bitmap
     * @return new union bitmap
     */
    public LongBitmapCounter or(LongBitmapCounter other) {
        Objects.requireNonNull(other, "other bitmap must not be null");
        this.rwLock.readLock().lock();
        other.rwLock.readLock().lock();
        try {
            int len = Math.max(this.words.length, other.words.length);
            long[] resultWords = new long[len];
            for (int i = 0; i < len; i++) {
                long w1 = i < this.words.length ? this.words[i] : 0L;
                long w2 = i < other.words.length ? other.words[i] : 0L;
                resultWords[i] = w1 | w2;
            }
            return new LongBitmapCounter(resultWords);
        } finally {
            other.rwLock.readLock().unlock();
            this.rwLock.readLock().unlock();
        }
    }

    /**
     * Returns a new LongBitmapCounter representing the bitwise XOR (symmetric difference) of this and other.
     *
     * @param other other bitmap
     * @return new XOR bitmap
     */
    public LongBitmapCounter xor(LongBitmapCounter other) {
        Objects.requireNonNull(other, "other bitmap must not be null");
        this.rwLock.readLock().lock();
        other.rwLock.readLock().lock();
        try {
            int len = Math.max(this.words.length, other.words.length);
            long[] resultWords = new long[len];
            for (int i = 0; i < len; i++) {
                long w1 = i < this.words.length ? this.words[i] : 0L;
                long w2 = i < other.words.length ? other.words[i] : 0L;
                resultWords[i] = w1 ^ w2;
            }
            return new LongBitmapCounter(resultWords);
        } finally {
            other.rwLock.readLock().unlock();
            this.rwLock.readLock().unlock();
        }
    }

    /**
     * Returns a new LongBitmapCounter representing the bitwise AND-NOT (relative complement / difference: this AND NOT other).
     *
     * @param other other bitmap
     * @return new difference bitmap
     */
    public LongBitmapCounter andNot(LongBitmapCounter other) {
        Objects.requireNonNull(other, "other bitmap must not be null");
        this.rwLock.readLock().lock();
        other.rwLock.readLock().lock();
        try {
            int len = this.words.length;
            long[] resultWords = new long[len];
            for (int i = 0; i < len; i++) {
                long otherWord = i < other.words.length ? other.words[i] : 0L;
                resultWords[i] = this.words[i] & ~otherWord;
            }
            return new LongBitmapCounter(resultWords);
        } finally {
            other.rwLock.readLock().unlock();
            this.rwLock.readLock().unlock();
        }
    }

    /**
     * Iterates over every set bit index in ascending order and passes it to the given consumer.
     *
     * @param consumer index receiver
     */
    public void forEachSetBit(LongConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        rwLock.readLock().lock();
        try {
            for (int i = 0; i < words.length; i++) {
                long word = words[i];
                while (word != 0L) {
                    long t = word & -word;
                    int bitInWord = Long.numberOfTrailingZeros(word);
                    consumer.accept(((long) i << ADDRESS_BITS_PER_WORD) + bitInWord);
                    word ^= t;
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Serializes this bitmap into a compact byte array.
     *
     * @return serialized byte array
     */
    public byte[] toByteArray() {
        rwLock.readLock().lock();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(words.length * Long.BYTES);
            for (long w : words) {
                buffer.putLong(w);
            }
            return buffer.array();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Deserializes a bitmap from the given byte array.
     *
     * @param data byte array generated by {@link #toByteArray()}
     * @return deserialized LongBitmapCounter
     */
    public static LongBitmapCounter fromByteArray(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length % Long.BYTES != 0) {
            throw new IllegalArgumentException("Byte array length must be multiple of 8: " + data.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        long[] words = new long[data.length / Long.BYTES];
        for (int i = 0; i < words.length; i++) {
            words[i] = buffer.getLong();
        }
        return new LongBitmapCounter(words);
    }

    private void ensureCapacity(int wordsRequired) {
        if (wordsRequired > words.length) {
            int newCap = Math.max(words.length * 2, wordsRequired);
            words = Arrays.copyOf(words, newCap);
        }
    }

    private static void checkIndex(long bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("bitIndex must be non-negative: " + bitIndex);
        }
    }
}
