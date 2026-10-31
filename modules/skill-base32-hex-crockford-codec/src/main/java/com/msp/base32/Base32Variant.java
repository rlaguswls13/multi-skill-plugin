package com.msp.base32;

/**
 * Supported Base32 alphabet variants.
 */
public enum Base32Variant {
    /**
     * RFC 4648 standard Base32 alphabet (A-Z, 2-7).
     * Pad character: '='
     */
    RFC4648("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", true),

    /**
     * RFC 4648 extended Hex Base32 alphabet (0-9, A-V).
     * Pad character: '='
     */
    RFC4648_HEX("0123456789ABCDEFGHIJKLMNOPQRSTUV", true),

    /**
     * Douglas Crockford's Base32 alphabet (0-9, A-Z excluding I, L, O, U).
     * Typically unpadded, case-insensitive, with error-tolerant normalization (I/L->1, O->0, hyphens ignored).
     */
    CROCKFORD("0123456789ABCDEFGHJKMNPQRSTVWXYZ", false);

    private final String alphabet;
    private final boolean defaultPadding;
    private final byte[] decodeTable;

    Base32Variant(String alphabet, boolean defaultPadding) {
        this.alphabet = alphabet;
        this.defaultPadding = defaultPadding;
        this.decodeTable = new byte[128];
        for (int i = 0; i < 128; i++) {
            this.decodeTable[i] = -1;
        }
        for (int i = 0; i < alphabet.length(); i++) {
            char ch = alphabet.charAt(i);
            this.decodeTable[ch] = (byte) i;
            if (Character.isLetter(ch)) {
                this.decodeTable[Character.toLowerCase(ch)] = (byte) i;
            }
        }
    }

    public String getAlphabet() {
        return alphabet;
    }

    public boolean isDefaultPadding() {
        return defaultPadding;
    }

    public char charAt(int index) {
        return alphabet.charAt(index);
    }

    public int decodeChar(char c) {
        if (c < 128) {
            return decodeTable[c];
        }
        return -1;
    }
}
