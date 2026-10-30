package com.msp.permission;

import java.util.*;

/**
 * 64-bit Long Bitmask Permission evaluator and calculator.
 * Supports static utility operations as well as fluent instance manipulation.
 */
public final class BitmaskPermission {

    private long mask;

    public BitmaskPermission(long initialMask) {
        this.mask = initialMask;
    }

    public BitmaskPermission() {
        this(0L);
    }

    public static BitmaskPermission of(long initialMask) {
        return new BitmaskPermission(initialMask);
    }

    public static BitmaskPermission of(PermissionFlag... flags) {
        BitmaskPermission bp = new BitmaskPermission();
        for (PermissionFlag flag : flags) {
            bp.grant(flag.mask());
        }
        return bp;
    }

    // Static Utility Methods

    public static boolean has(long userMask, long requiredFlag) {
        return (userMask & requiredFlag) == requiredFlag;
    }

    public static boolean hasAll(long userMask, long... requiredFlags) {
        for (long flag : requiredFlags) {
            if ((userMask & flag) != flag) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasAny(long userMask, long... flags) {
        for (long flag : flags) {
            if ((userMask & flag) != 0L) {
                return true;
            }
        }
        return false;
    }

    public static long grant(long userMask, long... flags) {
        long result = userMask;
        for (long flag : flags) {
            result |= flag;
        }
        return result;
    }

    public static long revoke(long userMask, long... flags) {
        long result = userMask;
        for (long flag : flags) {
            result &= ~flag;
        }
        return result;
    }

    public static long toggle(long userMask, long flag) {
        return userMask ^ flag;
    }

    public static List<String> describe(long userMask) {
        List<String> list = new ArrayList<>();
        for (PermissionFlag flag : PermissionFlag.values()) {
            if (has(userMask, flag.mask())) {
                list.add(flag.name());
            }
        }
        return Collections.unmodifiableList(list);
    }

    // Instance Fluent Methods

    public long getMask() {
        return mask;
    }

    public BitmaskPermission grant(long flag) {
        this.mask |= flag;
        return this;
    }

    public BitmaskPermission grant(PermissionFlag flag) {
        return grant(flag.mask());
    }

    public BitmaskPermission revoke(long flag) {
        this.mask &= ~flag;
        return this;
    }

    public BitmaskPermission revoke(PermissionFlag flag) {
        return revoke(flag.mask());
    }

    public BitmaskPermission toggle(long flag) {
        this.mask ^= flag;
        return this;
    }

    public BitmaskPermission toggle(PermissionFlag flag) {
        return toggle(flag.mask());
    }

    public boolean has(long flag) {
        return has(this.mask, flag);
    }

    public boolean has(PermissionFlag flag) {
        return has(this.mask, flag.mask());
    }

    public List<String> describe() {
        return describe(this.mask);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BitmaskPermission that)) return false;
        return mask == that.mask;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(mask);
    }

    @Override
    public String toString() {
        return "BitmaskPermission[mask=" + mask + ", permissions=" + describe() + "]";
    }
}
