package com.msp.ipbogon;

import java.util.Objects;

/**
 * Result of classifying an IPv4 or IPv6 address.
 *
 * @param normalizedIp        canonical string representation of the classified address
 * @param ipVersion           {@code 4} or {@code 6} (reflects unwrapped IPv4 if IPv4-mapped IPv6)
 * @param category            matched {@link IpCategory}
 * @param unwrappedFromIpv6   {@code true} if the input was an IPv4-mapped IPv6 address ({@code ::ffff:0:0/96})
 * @param matchedCidr         CIDR block that determined the classification (e.g. {@code 10.0.0.0/8})
 */
public record IpClassification(
        String normalizedIp,
        int ipVersion,
        IpCategory category,
        boolean unwrappedFromIpv6,
        String matchedCidr
) {
    public IpClassification {
        Objects.requireNonNull(normalizedIp, "normalizedIp must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(matchedCidr, "matchedCidr must not be null");
        if (ipVersion != 4 && ipVersion != 6) {
            throw new IllegalArgumentException("ipVersion must be 4 or 6: " + ipVersion);
        }
    }

    public boolean isPublic() {
        return category.isPublic();
    }

    public boolean isBogon() {
        return category.isBogon();
    }
}
