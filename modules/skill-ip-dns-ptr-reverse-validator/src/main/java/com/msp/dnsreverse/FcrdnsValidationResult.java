package com.msp.dnsreverse;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

/**
 * Result record for Forward-Confirmed Reverse DNS (FCrDNS) validation.
 *
 * @param isConfirmed      True if at least one PTR domain forward-resolves back to the client IP
 * @param clientIp         The tested client IP address
 * @param ptrDomains       All domain names returned by reverse DNS lookup (PTR)
 * @param confirmedDomains Domain names whose forward DNS records include the client IP
 * @param status           Categorized status of the validation
 * @param details          Human-readable message explaining the outcome
 */
public record FcrdnsValidationResult(
        boolean isConfirmed,
        InetAddress clientIp,
        List<String> ptrDomains,
        List<String> confirmedDomains,
        FcrdnsStatus status,
        String details
) {
    public FcrdnsValidationResult {
        ptrDomains = ptrDomains != null ? Collections.unmodifiableList(ptrDomains) : List.of();
        confirmedDomains = confirmedDomains != null ? Collections.unmodifiableList(confirmedDomains) : List.of();
    }
}
