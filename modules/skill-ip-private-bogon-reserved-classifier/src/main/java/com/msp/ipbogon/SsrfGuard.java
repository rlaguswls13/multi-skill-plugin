package com.msp.ipbogon;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Configurable SSRF (Server-Side Request Forgery) IP policy guard.
 *
 * <p>By default ({@link #strict()}), only {@link IpCategory#PUBLIC_UNICAST} addresses are allowed;
 * all private, loopback, link-local, CGNAT, cloud metadata, multicast, and reserved ranges are blocked.
 */
public final class SsrfGuard {

    private final Set<IpCategory> allowedCategories;
    private final List<CidrBlock> allowCidrs;
    private final List<CidrBlock> denyCidrs;
    private final boolean blockIpv4MappedIpv6;

    private SsrfGuard(
            Set<IpCategory> allowedCategories,
            List<CidrBlock> allowCidrs,
            List<CidrBlock> denyCidrs,
            boolean blockIpv4MappedIpv6
    ) {
        this.allowedCategories = Collections.unmodifiableSet(EnumSet.copyOf(allowedCategories));
        this.allowCidrs = List.copyOf(allowCidrs);
        this.denyCidrs = List.copyOf(denyCidrs);
        this.blockIpv4MappedIpv6 = blockIpv4MappedIpv6;
    }

    /**
     * Returns a strict SSRF guard that permits only {@link IpCategory#PUBLIC_UNICAST}.
     */
    public static SsrfGuard strict() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Evaluates the SSRF policy against a numeric IPv4 or IPv6 literal (no DNS lookup).
     */
    public Decision check(String ipLiteral) {
        byte[] raw = IpBogonClassifier.parseIpLiteral(ipLiteral.trim());
        return evaluate(raw);
    }

    /**
     * Evaluates the SSRF policy against a resolved {@link InetAddress}.
     */
    public Decision check(InetAddress address) {
        Objects.requireNonNull(address, "address must not be null");
        return evaluate(address.getAddress());
    }

    /**
     * Asserts that {@code ipLiteral} is allowed, throwing {@link SecurityException} if blocked.
     */
    public IpClassification assertAllowed(String ipLiteral) {
        Decision decision = check(ipLiteral);
        if (!decision.allowed()) {
            throw new SecurityException("SSRF policy blocked IP " + decision.classification().normalizedIp() + ": " + decision.reason());
        }
        return decision.classification();
    }

    private Decision evaluate(byte[] raw) {
        IpClassification cls = IpBogonClassifier.classify(raw);
        byte[] effectiveBytes = (raw.length == 16 && IpBogonClassifier.isIpv4MappedIpv6(raw))
                ? java.util.Arrays.copyOfRange(raw, 12, 16)
                : raw;

        // 1. Explicit deny CIDRs always win
        for (CidrBlock deny : denyCidrs) {
            if (deny.matches(effectiveBytes)) {
                return new Decision(false, "Matched explicit deny CIDR " + deny.raw(), cls);
            }
        }

        // 2. Optional strict IPv4-mapped IPv6 block
        if (blockIpv4MappedIpv6 && cls.unwrappedFromIpv6()) {
            return new Decision(false, "IPv4-mapped IPv6 addresses are blocked by policy", cls);
        }

        // 3. Explicit allow CIDRs override category blocks
        for (CidrBlock allow : allowCidrs) {
            if (allow.matches(effectiveBytes)) {
                return new Decision(true, "Matched explicit allow CIDR " + allow.raw(), cls);
            }
        }

        // 4. Category check
        if (allowedCategories.contains(cls.category())) {
            return new Decision(true, "Allowed category " + cls.category(), cls);
        }

        return new Decision(false, "Blocked category " + cls.category() + " (" + cls.matchedCidr() + ")", cls);
    }

    /**
     * Policy evaluation outcome.
     */
    public record Decision(boolean allowed, String reason, IpClassification classification) {
    }

    public static final class Builder {
        private final EnumSet<IpCategory> allowed = EnumSet.of(IpCategory.PUBLIC_UNICAST);
        private final List<CidrBlock> allowCidrs = new ArrayList<>();
        private final List<CidrBlock> denyCidrs = new ArrayList<>();
        private boolean blockIpv4MappedIpv6 = false;

        private Builder() {
        }

        public Builder allowCategory(IpCategory category) {
            allowed.add(Objects.requireNonNull(category, "category must not be null"));
            return this;
        }

        public Builder denyCategory(IpCategory category) {
            allowed.remove(Objects.requireNonNull(category, "category must not be null"));
            return this;
        }

        public Builder allowCidr(String cidr) {
            allowCidrs.add(CidrBlock.parse(cidr));
            return this;
        }

        public Builder denyCidr(String cidr) {
            denyCidrs.add(CidrBlock.parse(cidr));
            return this;
        }

        public Builder blockIpv4MappedIpv6(boolean block) {
            this.blockIpv4MappedIpv6 = block;
            return this;
        }

        public SsrfGuard build() {
            return new SsrfGuard(allowed, allowCidrs, denyCidrs, blockIpv4MappedIpv6);
        }
    }

    private record CidrBlock(String raw, byte[] network, int prefixLen) {

        static CidrBlock parse(String cidr) {
            Objects.requireNonNull(cidr, "cidr must not be null");
            int slash = cidr.indexOf('/');
            if (slash <= 0 || slash == cidr.length() - 1) {
                throw new IllegalArgumentException("Invalid CIDR notation: " + cidr);
            }
            byte[] addr = IpBogonClassifier.parseIpLiteral(cidr.substring(0, slash).trim());
            int prefix = Integer.parseInt(cidr.substring(slash + 1).trim());
            int maxBits = addr.length * 8;
            if (prefix < 0 || prefix > maxBits) {
                throw new IllegalArgumentException("CIDR prefix out of range (0.." + maxBits + "): " + cidr);
            }
            return new CidrBlock(cidr.trim(), addr, prefix);
        }

        boolean matches(byte[] addr) {
            if (addr.length != network.length) {
                return false;
            }
            int fullBytes = prefixLen / 8;
            int remBits = prefixLen % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != network[i]) {
                    return false;
                }
            }
            if (remBits > 0) {
                int mask = (0xFF << (8 - remBits)) & 0xFF;
                return (addr[fullBytes] & mask) == (network[fullBytes] & mask);
            }
            return true;
        }
    }
}
