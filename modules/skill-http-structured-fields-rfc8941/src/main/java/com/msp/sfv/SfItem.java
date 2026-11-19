package com.msp.sfv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 8941 Section 3.3 Item: a {@link BareItem} and ordered {@code Parameters}.
 */
public record SfItem(BareItem value, Map<String, BareItem> params) implements SfMember {

    public SfItem {
        Objects.requireNonNull(value, "Item bare value must not be null");
        Objects.requireNonNull(params, "Item parameters map must not be null");
        LinkedHashMap<String, BareItem> copy = new LinkedHashMap<>();
        for (Map.Entry<String, BareItem> entry : params.entrySet()) {
            SfMember.validateKey(entry.getKey());
            Objects.requireNonNull(entry.getValue(), "Parameter value must not be null for key: " + entry.getKey());
            copy.put(entry.getKey(), entry.getValue());
        }
        params = Collections.unmodifiableMap(copy);
    }

    public static SfItem of(BareItem value) {
        return new SfItem(value, Map.of());
    }

    public static SfItem of(BareItem value, Map<String, BareItem> params) {
        return new SfItem(value, params);
    }
}
