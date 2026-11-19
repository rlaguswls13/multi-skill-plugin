package com.msp.dnsreverse;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Forward-Confirmed Reverse DNS (FCrDNS) Validator.
 * RFC-compliant anti-spoofing mechanism commonly used by mail servers (SMTP MTA),
 * SSH servers, and access control firewalls.
 */
public class FcrdnsValidator {

    private final DnsResolver resolver;

    public FcrdnsValidator(DnsResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /**
     * Creates a validator backed by the standard system DNS resolver.
     */
    public static FcrdnsValidator system() {
        return new FcrdnsValidator(SystemDnsResolver.INSTANCE);
    }

    /**
     * Creates a validator backed by an in-memory DNS resolver.
     */
    public static FcrdnsValidator inMemory(InMemoryDnsResolver inMemoryResolver) {
        return new FcrdnsValidator(inMemoryResolver);
    }

    /**
     * Validates an IP string representation against Forward-Confirmed Reverse DNS.
     *
     * @param ipString IPv4 or IPv6 address string
     * @return Validation outcome
     */
    public FcrdnsValidationResult validate(String ipString) {
        if (ipString == null || ipString.isBlank()) {
            return new FcrdnsValidationResult(
                    false,
                    null,
                    List.of(),
                    List.of(),
                    FcrdnsStatus.DNS_ERROR,
                    "Client IP address string is null or empty"
            );
        }
        try {
            InetAddress ip = InetAddress.getByName(ipString.trim());
            return validate(ip);
        } catch (UnknownHostException e) {
            return new FcrdnsValidationResult(
                    false,
                    null,
                    List.of(),
                    List.of(),
                    FcrdnsStatus.DNS_ERROR,
                    "Invalid IP address format: " + ipString
            );
        }
    }

    /**
     * Performs three-step Forward-Confirmed Reverse DNS validation:
     * 1. PTR lookup for client IP -> yields domains
     * 2. Forward A/AAAA lookup for each resolved domain -> yields IPs
     * 3. Confirms if any forward IP matches the original client IP.
     *
     * @param clientIp Client InetAddress to validate
     * @return Detailed FCrDNS result
     */
    public FcrdnsValidationResult validate(InetAddress clientIp) {
        if (clientIp == null) {
            return new FcrdnsValidationResult(
                    false,
                    null,
                    List.of(),
                    List.of(),
                    FcrdnsStatus.DNS_ERROR,
                    "Client InetAddress must not be null"
            );
        }

        List<String> ptrDomains;
        try {
            ptrDomains = resolver.resolvePtr(clientIp);
        } catch (Exception e) {
            return new FcrdnsValidationResult(
                    false,
                    clientIp,
                    List.of(),
                    List.of(),
                    FcrdnsStatus.DNS_ERROR,
                    "Reverse DNS lookup failed with error: " + e.getMessage()
            );
        }

        if (ptrDomains == null || ptrDomains.isEmpty()) {
            return new FcrdnsValidationResult(
                    false,
                    clientIp,
                    List.of(),
                    List.of(),
                    FcrdnsStatus.NO_PTR_RECORD,
                    "No PTR reverse DNS record found for " + clientIp.getHostAddress()
            );
        }

        List<String> confirmedDomains = new ArrayList<>();
        boolean anyForwardSuccess = false;

        for (String domain : ptrDomains) {
            try {
                List<InetAddress> forwardIps = resolver.resolveForward(domain);
                if (forwardIps != null && !forwardIps.isEmpty()) {
                    anyForwardSuccess = true;
                    boolean matches = forwardIps.stream().anyMatch(fIp -> ipMatches(clientIp, fIp));
                    if (matches) {
                        confirmedDomains.add(domain);
                    }
                }
            } catch (Exception ignored) {
                // Ignore single domain resolution errors
            }
        }

        if (!confirmedDomains.isEmpty()) {
            return new FcrdnsValidationResult(
                    true,
                    clientIp,
                    ptrDomains,
                    confirmedDomains,
                    FcrdnsStatus.CONFIRMED,
                    "FCrDNS confirmed: " + confirmedDomains
            );
        }

        if (!anyForwardSuccess) {
            return new FcrdnsValidationResult(
                    false,
                    clientIp,
                    ptrDomains,
                    List.of(),
                    FcrdnsStatus.FORWARD_LOOKUP_FAILED,
                    "PTR record exists (" + ptrDomains + "), but forward DNS resolution failed"
            );
        }

        return new FcrdnsValidationResult(
                false,
                clientIp,
                ptrDomains,
                List.of(),
                FcrdnsStatus.IP_MISMATCH,
                "PTR domain resolved, but forward IPs do not include original client IP: " + clientIp.getHostAddress()
        );
    }

    private static boolean ipMatches(InetAddress ip1, InetAddress ip2) {
        if (ip1 == null || ip2 == null) {
            return false;
        }
        return Arrays.equals(ip1.getAddress(), ip2.getAddress());
    }
}
