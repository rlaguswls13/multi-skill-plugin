package com.msp.sfv;

import java.util.List;
import java.util.Objects;

/**
 * RFC 8941 Section 3.1 List: top-level ordered list of {@link SfMember} elements.
 */
public record SfList(List<SfMember> members) {
    public SfList {
        Objects.requireNonNull(members, "List members must not be null");
        members = List.copyOf(members);
    }

    public static SfList of(SfMember... members) {
        return new SfList(List.of(members));
    }
}
