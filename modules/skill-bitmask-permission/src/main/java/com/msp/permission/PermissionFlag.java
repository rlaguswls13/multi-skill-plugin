package com.msp.permission;

/**
 * Standard predefined 64-bit permission flags.
 */
public enum PermissionFlag {
    READ(1L << 0),       // 1
    WRITE(1L << 1),      // 2
    UPDATE(1L << 2),     // 4
    DELETE(1L << 3),     // 8
    EXECUTE(1L << 4),    // 16
    ADMIN(1L << 5),      // 32
    EXPORT(1L << 6),     // 64
    AUDIT(1L << 7);      // 128

    private final long mask;

    PermissionFlag(long mask) {
        this.mask = mask;
    }

    public long mask() {
        return mask;
    }
}
