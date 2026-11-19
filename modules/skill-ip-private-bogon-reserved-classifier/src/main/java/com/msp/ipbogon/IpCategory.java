package com.msp.ipbogon;

/**
 * Classification categories for IPv4 and IPv6 addresses.
 */
public enum IpCategory {

    PUBLIC_UNICAST(true, "Global Unicast"),
    PRIVATE_RFC1918(false, "RFC 1918"),
    UNIQUE_LOCAL_IPV6(false, "RFC 4193"),
    CGNAT(false, "RFC 6598"),
    LOOPBACK(false, "RFC 1122 / RFC 4291"),
    CLOUD_METADATA(false, "Cloud IMDS"),
    LINK_LOCAL(false, "RFC 3927 / RFC 4291"),
    MULTICAST(false, "RFC 5771 / RFC 4291"),
    BROADCAST(false, "RFC 919"),
    UNSPECIFIED(false, "RFC 1122 / RFC 4291"),
    DOCUMENTATION(false, "RFC 5737 / RFC 3849"),
    BENCHMARKING(false, "RFC 2544 / RFC 5180"),
    RESERVED_SPECIAL(false, "RFC 6890");

    private final boolean publicRoutable;
    private final String rfcReference;

    IpCategory(boolean publicRoutable, String rfcReference) {
        this.publicRoutable = publicRoutable;
        this.rfcReference = rfcReference;
    }

    public boolean isPublic() {
        return publicRoutable;
    }

    public boolean isBogon() {
        return !publicRoutable;
    }

    public String rfcReference() {
        return rfcReference;
    }
}
