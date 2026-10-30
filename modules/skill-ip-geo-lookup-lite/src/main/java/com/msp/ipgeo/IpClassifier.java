package com.msp.ipgeo;

import java.util.List;

/**
 * IP 주소 유형(사설망, 루프백, 공인 IP 등) 안전 판별기.
 */
public final class IpClassifier {

    private static final List<Cidr> PRIVATE_CIDRS = List.of(
            Cidr.of("10.0.0.0/8"),
            Cidr.of("172.16.0.0/12"),
            Cidr.of("192.168.0.0/16")
    );

    private static final List<Cidr> LOOPBACK_CIDRS = List.of(
            Cidr.of("127.0.0.0/8")
    );

    private static final Cidr CARRIER_GRADE_NAT = Cidr.of("100.64.0.0/10");
    private static final Cidr LINK_LOCAL = Cidr.of("169.254.0.0/16");

    private IpClassifier() {}

    public static boolean isPrivate(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return PRIVATE_CIDRS.stream().anyMatch(cidr -> cidr.matches(ip));
    }

    public static boolean isLoopback(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String trimmed = ip.trim();
        if ("::1".equals(trimmed) || "localhost".equalsIgnoreCase(trimmed)) {
            return true;
        }
        return LOOPBACK_CIDRS.stream().anyMatch(cidr -> cidr.matches(trimmed));
    }

    public static boolean isCarrierGradeNat(String ip) {
        return CARRIER_GRADE_NAT.matches(ip);
    }

    public static boolean isLinkLocal(String ip) {
        return LINK_LOCAL.matches(ip);
    }

    public static boolean isPublic(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return !isPrivate(ip) && !isLoopback(ip) && !isCarrierGradeNat(ip) && !isLinkLocal(ip);
    }
}
