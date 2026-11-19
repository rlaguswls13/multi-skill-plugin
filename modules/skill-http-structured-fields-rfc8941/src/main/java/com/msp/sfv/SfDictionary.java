package com.msp.sfv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 8941 Section 3.2 Dictionary: ordered map from {@code key} to {@link SfMember}.
 */
public record SfDictionary(Map<String, SfMember> entries) {
    public SfDictionary {
        Objects.requireNonNull(entries, "Dictionary entries must not be null");
        LinkedHashMap<String, SfMember> copy = new LinkedHashMap<>();
        for (Map.Entry<String, SfMember> entry : entries.entrySet()) {
            SfMember.validateKey(entry.getKey());
            Objects.requireNonNull(entry.getValue(), "Dictionary member must not be null for key: " + entry.getKey());
            copy.put(entry.getKey(), entry.getValue());
        }
        entries = Collections.unmodifiableMap(copy);
    }
}
