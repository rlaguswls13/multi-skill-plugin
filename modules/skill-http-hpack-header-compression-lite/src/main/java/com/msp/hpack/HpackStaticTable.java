package com.msp.hpack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * RFC 7541 Appendix A - Static Table Definition.
 * Contains 61 pre-defined header fields indexed 1 to 61.
 */
public final class HpackStaticTable {

    private static final HeaderField[] STATIC_TABLE = new HeaderField[] {
            null, // 0 is unused, 1-based indexing
            new HeaderField(":authority", ""),
            new HeaderField(":method", "GET"),
            new HeaderField(":method", "POST"),
            new HeaderField(":path", "/"),
            new HeaderField(":path", "/index.html"),
            new HeaderField(":scheme", "http"),
            new HeaderField(":scheme", "https"),
            new HeaderField(":status", "200"),
            new HeaderField(":status", "204"),
            new HeaderField(":status", "206"),
            new HeaderField(":status", "304"),
            new HeaderField(":status", "400"),
            new HeaderField(":status", "404"),
            new HeaderField(":status", "500"),
            new HeaderField("accept-charset", ""),
            new HeaderField("accept-encoding", "gzip, deflate"),
            new HeaderField("accept-language", ""),
            new HeaderField("accept-ranges", ""),
            new HeaderField("accept", ""),
            new HeaderField("access-control-allow-origin", ""),
            new HeaderField("age", ""),
            new HeaderField("allow", ""),
            new HeaderField("authorization", ""),
            new HeaderField("cache-control", ""),
            new HeaderField("content-disposition", ""),
            new HeaderField("content-encoding", ""),
            new HeaderField("content-language", ""),
            new HeaderField("content-length", ""),
            new HeaderField("content-location", ""),
            new HeaderField("content-range", ""),
            new HeaderField("content-type", ""),
            new HeaderField("cookie", ""),
            new HeaderField("date", ""),
            new HeaderField("etag", ""),
            new HeaderField("expect", ""),
            new HeaderField("expires", ""),
            new HeaderField("from", ""),
            new HeaderField("host", ""),
            new HeaderField("if-match", ""),
            new HeaderField("if-modified-since", ""),
            new HeaderField("if-none-match", ""),
            new HeaderField("if-range", ""),
            new HeaderField("if-unmodified-since", ""),
            new HeaderField("last-modified", ""),
            new HeaderField("link", ""),
            new HeaderField("location", ""),
            new HeaderField("max-forwards", ""),
            new HeaderField("proxy-authenticate", ""),
            new HeaderField("proxy-authorization", ""),
            new HeaderField("range", ""),
            new HeaderField("referer", ""),
            new HeaderField("refresh", ""),
            new HeaderField("retry-after", ""),
            new HeaderField("server", ""),
            new HeaderField("set-cookie", ""),
            new HeaderField("strict-transport-security", ""),
            new HeaderField("transfer-encoding", ""),
            new HeaderField("user-agent", ""),
            new HeaderField("vary", ""),
            new HeaderField("via", ""),
            new HeaderField("www-authenticate", "")
    };

    private static final Map<String, Integer> NAME_TO_FIRST_INDEX;
    private static final Map<String, Integer> EXACT_MATCH_INDEX;

    static {
        Map<String, Integer> nameMap = new HashMap<>();
        Map<String, Integer> exactMap = new HashMap<>();
        for (int i = 1; i < STATIC_TABLE.length; i++) {
            HeaderField field = STATIC_TABLE[i];
            nameMap.putIfAbsent(field.name(), i);
            exactMap.putIfAbsent(field.name() + "\0" + field.value(), i);
        }
        NAME_TO_FIRST_INDEX = Collections.unmodifiableMap(nameMap);
        EXACT_MATCH_INDEX = Collections.unmodifiableMap(exactMap);
    }

    private HpackStaticTable() {}

    public static int size() {
        return STATIC_TABLE.length - 1; // 61
    }

    public static HeaderField get(int index) {
        if (index < 1 || index >= STATIC_TABLE.length) {
            throw new IndexOutOfBoundsException("Static table index out of bounds: " + index);
        }
        return STATIC_TABLE[index];
    }

    public static int findExact(String name, String value) {
        return EXACT_MATCH_INDEX.getOrDefault(name + "\0" + value, -1);
    }

    public static int findName(String name) {
        return NAME_TO_FIRST_INDEX.getOrDefault(name, -1);
    }
}
