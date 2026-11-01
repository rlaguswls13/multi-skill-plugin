package com.msp.bgp;

public enum IpVersion {
    IPV4(32),
    IPV6(128);

    private final int bitLength;

    IpVersion(int bitLength) {
        this.bitLength = bitLength;
    }

    public int bitLength() {
        return bitLength;
    }
}
