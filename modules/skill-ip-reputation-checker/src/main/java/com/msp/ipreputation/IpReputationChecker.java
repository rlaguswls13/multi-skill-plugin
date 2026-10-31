package com.msp.ipreputation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * High-performance bitmask-based IPv4 reputation checker and CIDR filter.
 * Thread-safe implementation with whitelist-first priority evaluation.
 */
public class IpReputationChecker {

    private record CidrRule(String original, long network, long mask, int prefix, String reason) {
        boolean matches(long ipLong) {
            return (ipLong & mask) == network;
        }
    }

    private final List<CidrRule> allowRules = new CopyOnWriteArrayList<>();
    private final List<CidrRule> blockRules = new CopyOnWriteArrayList<>();

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    public IpReputationChecker() {
    }

    /**
     * Blocks a CIDR network range (e.g. "10.0.0.0/8", "192.168.1.0/24") with a default reason.
     */
    public IpReputationChecker blockCidr(String cidr) {
        return blockCidr(cidr, "Blocked CIDR range: " + cidr);
    }

    /**
     * Blocks a CIDR network range with a specific reason.
     */
    public IpReputationChecker blockCidr(String cidr, String reason) {
        blockRules.add(parseRule(cidr, reason));
        return this;
    }

    /**
     * Blocks a single IP address (e.g. "203.0.113.42") with a default reason.
     */
    public IpReputationChecker blockIp(String ip) {
        return blockIp(ip, "Blocked IP: " + ip);
    }

    /**
     * Blocks a single IP address with a specific reason.
     */
    public IpReputationChecker blockIp(String ip, String reason) {
        return blockCidr(ip.contains("/") ? ip : ip + "/32", reason);
    }

    /**
     * Whitelists a CIDR network range. Whitelist takes precedence over blocklist.
     */
    public IpReputationChecker allowCidr(String cidr) {
        return allowCidr(cidr, "Whitelisted CIDR range: " + cidr);
    }

    /**
     * Whitelists a CIDR network range with a specific reason.
     */
    public IpReputationChecker allowCidr(String cidr, String reason) {
        allowRules.add(parseRule(cidr, reason));
        return this;
    }

    /**
     * Whitelists a single IP address.
     */
    public IpReputationChecker allowIp(String ip) {
        return allowIp(ip, "Whitelisted IP: " + ip);
    }

    /**
     * Whitelists a single IP address with a specific reason.
     */
    public IpReputationChecker allowIp(String ip, String reason) {
        return allowCidr(ip.contains("/") ? ip : ip + "/32", reason);
    }

    /**
     * Checks if the given IP address is blocked.
     * Whitelisted IPs always return false even if covered by a block rule.
     */
    public boolean isBlocked(String ip) {
        return check(ip).blocked();
    }

    /**
     * Evaluates reputation and returns detailed match results.
     */
    public ReputationCheckResult check(String ip) {
        Objects.requireNonNull(ip, "IP address must not be null");
        long ipLong = ipToLong(ip.trim());

        // 1. Whitelist evaluation (highest priority)
        for (CidrRule rule : allowRules) {
            if (rule.matches(ipLong)) {
                return ReputationCheckResult.allowed(rule.original(), rule.reason());
            }
        }

        // 2. Blocklist evaluation
        for (CidrRule rule : blockRules) {
            if (rule.matches(ipLong)) {
                return ReputationCheckResult.blocked(rule.original(), rule.reason());
            }
        }

        // 3. Clean default
        return ReputationCheckResult.clean();
    }

    /**
     * Clears all configured whitelist and blacklist rules.
     */
    public void clear() {
        allowRules.clear();
        blockRules.clear();
    }

    public int getAllowRuleCount() {
        return allowRules.size();
    }

    public int getBlockRuleCount() {
        return blockRules.size();
    }

    // --- Helper bitmask methods ---

    private CidrRule parseRule(String cidrStr, String reason) {
        Objects.requireNonNull(cidrStr, "CIDR string must not be null");
        String trimmed = cidrStr.trim();
        String ipPart;
        int prefix;

        if (trimmed.contains("/")) {
            String[] parts = trimmed.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR format: " + cidrStr);
            }
            ipPart = parts[0].trim();
            try {
                prefix = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR prefix: " + parts[1], e);
            }
            if (prefix < 0 || prefix > 32) {
                throw new IllegalArgumentException("CIDR prefix must be between 0 and 32: " + prefix);
            }
        } else {
            ipPart = trimmed;
            prefix = 32;
        }

        long ipLong = ipToLong(ipPart);
        long mask = prefixToMask(prefix);
        long network = ipLong & mask;

        return new CidrRule(trimmed, network, mask, prefix, reason);
    }

    public static long prefixToMask(int prefix) {
        if (prefix == 0) {
            return 0L;
        }
        return (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    }

    public static long ipToLong(String ip) {
        if (ip == null || !IPV4_PATTERN.matcher(ip).matches()) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }

        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            long octet = Long.parseLong(octets[i]);
            result = (result << 8) | octet;
        }
        return result & 0xFFFFFFFFL;
    }
}
