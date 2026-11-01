package com.msp.x509;

import java.util.Collections;
import java.util.List;

/**
 * Subject Alternative Names (SAN) extracted from X.509 Extension (OID 2.5.29.17).
 */
public record SubjectAltNames(
        List<String> dnsNames,
        List<String> ipAddresses,
        List<String> uris,
        List<String> emailAddresses
) {
    public static final SubjectAltNames EMPTY = new SubjectAltNames(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
    );

    public SubjectAltNames {
        dnsNames = dnsNames != null ? List.copyOf(dnsNames) : Collections.emptyList();
        ipAddresses = ipAddresses != null ? List.copyOf(ipAddresses) : Collections.emptyList();
        uris = uris != null ? List.copyOf(uris) : Collections.emptyList();
        emailAddresses = emailAddresses != null ? List.copyOf(emailAddresses) : Collections.emptyList();
    }

    public boolean isEmpty() {
        return dnsNames.isEmpty() && ipAddresses.isEmpty() && uris.isEmpty() && emailAddresses.isEmpty();
    }
}
