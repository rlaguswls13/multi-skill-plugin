package com.msp.x509;

/**
 * Standard KeyUsage bit string flags defined in RFC 5280 Section 4.2.1.3.
 */
public record KeyUsageFlags(
        boolean digitalSignature,
        boolean nonRepudiation,
        boolean keyEncipherment,
        boolean dataEncipherment,
        boolean keyAgreement,
        boolean keyCertSign,
        boolean cRLSign,
        boolean encipherOnly,
        boolean decipherOnly
) {
    public static final KeyUsageFlags NONE = new KeyUsageFlags(
            false, false, false, false, false, false, false, false, false
    );

    public static KeyUsageFlags fromArray(boolean[] usage) {
        if (usage == null || usage.length == 0) {
            return NONE;
        }
        return new KeyUsageFlags(
                usage.length > 0 && usage[0],
                usage.length > 1 && usage[1],
                usage.length > 2 && usage[2],
                usage.length > 3 && usage[3],
                usage.length > 4 && usage[4],
                usage.length > 5 && usage[5],
                usage.length > 6 && usage[6],
                usage.length > 7 && usage[7],
                usage.length > 8 && usage[8]
        );
    }
}
