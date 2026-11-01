package com.msp.rdap;

import java.util.Collections;
import java.util.List;

/**
 * Represents an RDAP Entity (e.g. Registrant, Administrative, Technical, or Abuse contact).
 */
public record RdapEntityRecord(
        String handle,
        List<String> roles,
        String formattedName,
        String organization,
        String email,
        String phone,
        String address
) {
    public RdapEntityRecord {
        roles = roles != null ? List.copyOf(roles) : Collections.emptyList();
    }
}
