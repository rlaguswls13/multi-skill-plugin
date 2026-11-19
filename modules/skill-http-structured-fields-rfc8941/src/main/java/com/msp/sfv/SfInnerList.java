package com.msp.sfv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 8941 Section 3.1.1 Inner List: an ordered list of {@link SfItem}s with associated parameters.
 */
public record SfInnerList(List<SfItem> items, Map<String, BareItem> params) implements SfMember {

    public SfInnerList {
        Objects.requireNonNull(items, "InnerList items must not be null");
        Objects.requireNonNull(params, "InnerList params must not be null");
        items = List.copyOf(items);
        LinkedHashMap<String, BareItem> copy = new LinkedHashMap<>();
        for (Map.Entry<String, BareItem> entry : params.entrySet()) {
            SfMember.validateKey(entry.getKey());
            Objects.requireNonNull(entry.getValue(), "Parameter value must not be null for key: " + entry.getKey());
            copy.put(entry.getKey(), entry.getValue());
        }
        params = Collections.unmodifiableMap(copy);
    }

    public static SfInnerList of(List<SfItem> items) {
        return new SfInnerList(items, Map.of());
    }

    public static SfInnerList of(List<SfItem> items, Map<String, BareItem> params) {
        return new SfInnerList(items, params);
    }
}
