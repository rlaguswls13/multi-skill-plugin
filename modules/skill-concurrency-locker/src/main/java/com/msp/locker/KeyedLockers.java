package com.msp.locker;

/**
 * Unified factory facade for creating KeyedLocker instances.
 */
public final class KeyedLockers {

    private KeyedLockers() {
    }

    /**
     * Creates a reference-counted KeyedLocker with automatic garbage collection of unused keys.
     */
    public static <K> KeyedLocker<K> create() {
        return new RefCountKeyedLocker<>(false);
    }

    /**
     * Creates a reference-counted KeyedLocker with configurable fairness.
     */
    public static <K> KeyedLocker<K> create(boolean fair) {
        return new RefCountKeyedLocker<>(fair);
    }

    /**
     * Creates a Striped KeyedLocker with the specified number of stripes.
     */
    public static <K> KeyedLocker<K> striped(int stripeCount) {
        return new StripedKeyedLocker<>(stripeCount, false);
    }

    /**
     * Creates a Striped KeyedLocker with the specified number of stripes and fairness.
     */
    public static <K> KeyedLocker<K> striped(int stripeCount, boolean fair) {
        return new StripedKeyedLocker<>(stripeCount, fair);
    }
}
