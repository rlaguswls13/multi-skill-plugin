package com.msp.dnswire;

/**
 * Standard DNS CLASS values according to RFC 1035.
 */
public final class DnsClass {
    public static final int IN = 1;     // Internet
    public static final int CS = 2;     // CSNET (obsolete)
    public static final int CH = 3;     // CHAOS
    public static final int HS = 4;     // Hesiod
    public static final int ANY = 255;  // Any class

    private DnsClass() {}
}
