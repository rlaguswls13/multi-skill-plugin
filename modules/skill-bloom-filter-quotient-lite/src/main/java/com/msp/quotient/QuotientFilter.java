package com.msp.quotient;

import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * High-performance Quotient Filter supporting fast membership testing, cache-friendly linear probing,
 * and true element deletion.
 *
 * <p>A Quotient Filter splits a hash into a q-bit quotient (the canonical bucket) and an r-bit remainder (fingerprint).
 * Each slot maintains 3 status bits:
 * <ul>
 *     <li><b>isOccupied:</b> indicates whether the bucket is the canonical home of any run in the table.</li>
 *     <li><b>isContinuation:</b> indicates whether the slot holds a remainder that is part of a run but not its first element.</li>
 *     <li><b>isShifted:</b> indicates whether the remainder in this slot has been shifted away from its canonical bucket.</li>
 * </ul>
 *
 * @param <T> element type
 */
public final class QuotientFilter<T> {

    private static final long MASK_OCCUPIED = 1L;
    private static final long MASK_CONTINUATION = 2L;
    private static final long MASK_SHIFTED = 4L;
    private static final long MASK_STATUS = 7L;

    private final int qBits;
    private final int rBits;
    private final int capacity; // 2^q
    private final int mask;     // capacity - 1
    private final long remainderMask;
    private final int maxElements;
    private final ToLongFunction<T> hasher;

    // Each slot: [remainder (bits 3..63)] [shifted (bit 2)] [continuation (bit 1)] [occupied (bit 0)]
    private final long[] table;
    private int count;

    private QuotientFilter(int qBits, int rBits, ToLongFunction<T> hasher) {
        if (qBits < 3 || qBits > 28) {
            throw new IllegalArgumentException("Quotient bits must be between 3 and 28, got: " + qBits);
        }
        if (rBits < 1 || rBits > 56) {
            throw new IllegalArgumentException("Remainder bits must be between 1 and 56, got: " + rBits);
        }
        if (qBits + rBits > 63) {
            throw new IllegalArgumentException("Total bits (q + r) cannot exceed 63, got: " + (qBits + rBits));
        }

        this.qBits = qBits;
        this.rBits = rBits;
        this.capacity = 1 << qBits;
        this.mask = capacity - 1;
        this.remainderMask = (1L << rBits) - 1;
        this.maxElements = (int) (capacity * 0.90); // default max load factor 90%
        this.hasher = Objects.requireNonNull(hasher, "hasher cannot be null");
        this.table = new long[capacity];
        this.count = 0;
    }

    public static <T> QuotientFilter<T> create(int quotientBits, int remainderBits, ToLongFunction<T> hasher) {
        return new QuotientFilter<>(quotientBits, remainderBits, hasher);
    }

    public static <T> QuotientFilter<T> create(int quotientBits, int remainderBits) {
        return new QuotientFilter<>(quotientBits, remainderBits, item -> hash64(Objects.hashCode(item)));
    }

    public static QuotientFilter<String> ofStrings(int quotientBits, int remainderBits) {
        return new QuotientFilter<>(quotientBits, remainderBits, QuotientFilter::hashString);
    }

    public static <T> QuotientFilter<T> withCapacity(int expectedCapacity, double fpp) {
        if (expectedCapacity <= 0) {
            throw new IllegalArgumentException("Expected capacity must be > 0");
        }
        if (fpp <= 0.0 || fpp >= 1.0) {
            throw new IllegalArgumentException("FPP must be in (0, 1)");
        }

        // Target load factor ~0.75
        double requiredSlots = expectedCapacity / 0.75;
        int q = 3;
        while ((1 << q) < requiredSlots && q < 28) {
            q++;
        }

        // fpp ≈ 2^-r
        int r = (int) Math.ceil(-Math.log(fpp) / Math.log(2.0));
        if (r < 4) r = 4;
        if (q + r > 60) r = 60 - q;

        return new QuotientFilter<>(q, r, item -> hash64(Objects.hashCode(item)));
    }

    public synchronized boolean put(T item) {
        long hash = hasher.applyAsLong(item);
        return putHash(hash);
    }

    public synchronized boolean putHash(long hash) {
        if (count >= maxElements) {
            throw new IllegalStateException("QuotientFilter capacity exceeded (count=" + count + ", max=" + maxElements + ")");
        }

        int q = getQuotient(hash);
        long r = getRemainder(hash);

        // Case 1: Run q does not exist yet
        if (!isOccupied(q)) {
            if (isSlotEmpty(q)) {
                // Canonical slot is free: place directly
                setSlot(q, r, true, false, false);
                count++;
                return true;
            }

            // Slot q is occupied by an earlier run. Find insertion point for run q.
            int ins = findRunStartForNewRun(q);
            shiftRight(ins);
            setSlot(ins, r, isOccupied(ins), false, ins != q);
            setOccupied(q, true);
            count++;
            return true;
        }

        // Case 2: Run q already exists
        int start = findRunStart(q);
        int scan = start;
        int ins = -1;
        boolean insertAsContinuation = false;

        while (true) {
            long curRem = remainder(scan);
            if (curRem == r) {
                return false; // element already exists (set semantics)
            }
            if (curRem > r) {
                ins = scan;
                insertAsContinuation = (scan != start);
                break;
            }
            int next = (scan + 1) & mask;
            if (!isContinuation(next)) {
                ins = next;
                insertAsContinuation = true;
                break;
            }
            scan = next;
        }

        // Shift elements starting from ins right by 1
        shiftRight(ins);

        if (ins == start) {
            // New element becomes the new start of run q
            setSlot(ins, r, isOccupied(ins), false, ins != q);
            // The previous start was shifted right to (ins + 1) and becomes a continuation
            int shiftedStart = (ins + 1) & mask;
            setContinuation(shiftedStart, true);
        } else {
            // New element is inserted inside or at the end of run q
            setSlot(ins, r, isOccupied(ins), insertAsContinuation, true);
        }

        count++;
        return true;
    }

    public synchronized boolean contains(T item) {
        long hash = hasher.applyAsLong(item);
        return containsHash(hash);
    }

    public synchronized boolean containsHash(long hash) {
        int q = getQuotient(hash);
        long r = getRemainder(hash);

        if (!isOccupied(q)) {
            return false;
        }

        int scan = findRunStart(q);
        while (true) {
            long curRem = remainder(scan);
            if (curRem == r) {
                return true;
            }
            if (curRem > r) {
                return false;
            }
            int next = (scan + 1) & mask;
            if (!isContinuation(next)) {
                return false;
            }
            scan = next;
        }
    }

    public synchronized boolean remove(T item) {
        long hash = hasher.applyAsLong(item);
        return removeHash(hash);
    }

    public synchronized boolean removeHash(long hash) {
        int q = getQuotient(hash);
        long r = getRemainder(hash);

        if (!isOccupied(q)) {
            return false;
        }

        int start = findRunStart(q);
        int scan = start;
        int target = -1;

        while (true) {
            long curRem = remainder(scan);
            if (curRem == r) {
                target = scan;
                break;
            }
            if (curRem > r) {
                return false;
            }
            int next = (scan + 1) & mask;
            if (!isContinuation(next)) {
                return false;
            }
            scan = next;
        }

        // Target found at target
        int nextSlot = (target + 1) & mask;
        boolean hasRemainingInRun;
        boolean targetWasStart = (target == start);

        if (targetWasStart) {
            if (isContinuation(nextSlot)) {
                // The element at nextSlot will shift left into target and become start of run
                hasRemainingInRun = true;
            } else {
                // target was the only element in run q
                hasRemainingInRun = false;
                setOccupied(q, false);
            }
        } else {
            hasRemainingInRun = true;
        }

        // Shift subsequent elements left
        shiftLeft(target, q, targetWasStart, hasRemainingInRun);
        count--;
        return true;
    }

    public synchronized void clear() {
        for (int i = 0; i < capacity; i++) {
            table[i] = 0L;
        }
        count = 0;
    }

    public int getCount() {
        return count;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getQuotientBits() {
        return qBits;
    }

    public int getRemainderBits() {
        return rBits;
    }

    public double getLoadFactor() {
        return (double) count / capacity;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    // --- Internal Navigation & Manipulation ---

    private int getQuotient(long hash) {
        return (int) ((hash >>> rBits) & mask);
    }

    private long getRemainder(long hash) {
        return hash & remainderMask;
    }

    private boolean isOccupied(int slot) {
        return (table[slot] & MASK_OCCUPIED) != 0;
    }

    private void setOccupied(int slot, boolean occupied) {
        if (occupied) {
            table[slot] |= MASK_OCCUPIED;
        } else {
            table[slot] &= ~MASK_OCCUPIED;
        }
    }

    private boolean isContinuation(int slot) {
        return (table[slot] & MASK_CONTINUATION) != 0;
    }

    private void setContinuation(int slot, boolean continuation) {
        if (continuation) {
            table[slot] |= MASK_CONTINUATION;
        } else {
            table[slot] &= ~MASK_CONTINUATION;
        }
    }

    private boolean isShifted(int slot) {
        return (table[slot] & MASK_SHIFTED) != 0;
    }

    private long remainder(int slot) {
        return table[slot] >>> 3;
    }

    private boolean isSlotEmpty(int slot) {
        return (table[slot] & MASK_STATUS) == 0;
    }

    private void setSlot(int slot, long rem, boolean occupied, boolean continuation, boolean shifted) {
        long val = (rem << 3);
        if (occupied) val |= MASK_OCCUPIED;
        if (continuation) val |= MASK_CONTINUATION;
        if (shifted) val |= MASK_SHIFTED;
        table[slot] = val;
    }

    private int findClusterStart(int from) {
        int s = from;
        while (isShifted(s)) {
            s = (s - 1 + capacity) & mask;
        }
        return s;
    }

    private int findRunStart(int q) {
        if (!isShifted(q)) {
            return q;
        }

        int s = findClusterStart(q);
        int b = s;
        int runsToSkip = 0;
        while (b != q) {
            if (isOccupied(b)) {
                runsToSkip++;
            }
            b = (b + 1) & mask;
        }

        int curr = s;
        while (runsToSkip > 0) {
            do {
                curr = (curr + 1) & mask;
            } while (isContinuation(curr));
            runsToSkip--;
        }
        return curr;
    }

    private int findRunStartForNewRun(int q) {
        int s = findClusterStart(q);
        int b = s;
        int runsToSkip = 0;
        while (b != q) {
            if (isOccupied(b)) {
                runsToSkip++;
            }
            b = (b + 1) & mask;
        }

        int curr = s;
        while (runsToSkip > 0) {
            do {
                curr = (curr + 1) & mask;
            } while (isContinuation(curr));
            runsToSkip--;
        }
        return curr;
    }

    private void shiftRight(int ins) {
        int emptySlot = ins;
        while (!isSlotEmpty(emptySlot)) {
            emptySlot = (emptySlot + 1) & mask;
        }

        int curr = emptySlot;
        while (curr != ins) {
            int prev = (curr - 1 + capacity) & mask;
            boolean occ = isOccupied(curr);
            setSlot(curr, remainder(prev), occ, isContinuation(prev), true);
            curr = prev;
        }
    }

    private void shiftLeft(int target, int deletedQuotient, boolean targetWasStart, boolean hasRemainingInRun) {
        int curr = (target + 1) & mask;
        int prev = target;

        int curBucket;
        if (targetWasStart && !hasRemainingInRun) {
            curBucket = nextOccupiedBucket(deletedQuotient);
        } else if (!targetWasStart && !isContinuation(curr)) {
            curBucket = nextOccupiedBucket(deletedQuotient);
        } else {
            curBucket = deletedQuotient;
        }

        boolean firstShift = true;

        while (!isSlotEmpty(curr) && isShifted(curr)) {
            long rem = remainder(curr);
            boolean originalCont = isContinuation(curr);
            boolean newCont = originalCont;

            if (firstShift) {
                if (targetWasStart && hasRemainingInRun) {
                    newCont = false;
                }
            } else {
                if (!originalCont) {
                    curBucket = nextOccupiedBucket(curBucket);
                }
            }

            boolean shifted = newCont || (prev != curBucket);

            boolean occ = isOccupied(prev);
            setSlot(prev, rem, occ, newCont, shifted);

            firstShift = false;
            prev = curr;
            curr = (curr + 1) & mask;
        }

        // Clear the vacant slot at prev (keeping its occupied bit)
        table[prev] = isOccupied(prev) ? MASK_OCCUPIED : 0L;
    }

    private int nextOccupiedBucket(int from) {
        int b = (from + 1) & mask;
        while (b != from) {
            if (isOccupied(b)) {
                return b;
            }
            b = (b + 1) & mask;
        }
        return -1;
    }

    // --- Standard Hashing Utilities ---

    public static long hash64(long x) {
        x ^= x >>> 30;
        x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27;
        x *= 0x94d049bb133111ebL;
        x ^= x >>> 31;
        return x;
    }

    public static long hashString(String s) {
        if (s == null) return 0L;
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return hash64(h);
    }
}
