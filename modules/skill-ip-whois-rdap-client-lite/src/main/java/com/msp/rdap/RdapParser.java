package com.msp.rdap;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Pure Java 21 parser for IANA RDAP (Registration Data Access Protocol) JSON documents (RFC 7483).
 */
public final class RdapParser {

    private RdapParser() {
    }

    /**
     * Parses an RDAP IP network response JSON string into {@link RdapNetworkRecord}.
     */
    @SuppressWarnings("unchecked")
    public static RdapNetworkRecord parseNetwork(String json) {
        Map<String, Object> map = SimpleJsonParser.parseObject(json);
        if (map.isEmpty()) {
            throw new IllegalArgumentException("Invalid or empty RDAP JSON payload");
        }

        String handle = getString(map, "handle");
        String startAddress = getString(map, "startAddress");
        String endAddress = getString(map, "endAddress");
        String ipVersion = getString(map, "ipVersion");
        String name = getString(map, "name");
        String type = getString(map, "type");
        String country = getString(map, "country");
        String port43 = getString(map, "port43");

        List<String> status = getListOfStrings(map, "status");
        List<RdapEntityRecord> entities = parseEntities((List<Object>) map.get("entities"));
        Map<String, Instant> events = parseEvents((List<Object>) map.get("events"));
        List<String> notices = parseNotices((List<Object>) map.get("notices"));

        return new RdapNetworkRecord(
                handle,
                startAddress,
                endAddress,
                ipVersion,
                name,
                type,
                country,
                status,
                port43,
                entities,
                events,
                notices
        );
    }

    /**
     * Parses an RDAP Autnum / ASN response JSON string into {@link RdapAutnumRecord}.
     */
    @SuppressWarnings("unchecked")
    public static RdapAutnumRecord parseAutnum(String json) {
        Map<String, Object> map = SimpleJsonParser.parseObject(json);
        if (map.isEmpty()) {
            throw new IllegalArgumentException("Invalid or empty RDAP JSON payload");
        }

        String handle = getString(map, "handle");
        long startAutnum = getLong(map, "startAutnum", -1L);
        long endAutnum = getLong(map, "endAutnum", -1L);
        String name = getString(map, "name");
        String type = getString(map, "type");
        String country = getString(map, "country");
        String port43 = getString(map, "port43");

        List<String> status = getListOfStrings(map, "status");
        List<RdapEntityRecord> entities = parseEntities((List<Object>) map.get("entities"));
        Map<String, Instant> events = parseEvents((List<Object>) map.get("events"));
        List<String> notices = parseNotices((List<Object>) map.get("notices"));

        return new RdapAutnumRecord(
                handle,
                startAutnum,
                endAutnum,
                name,
                type,
                country,
                status,
                port43,
                entities,
                events,
                notices
        );
    }

    @SuppressWarnings("unchecked")
    public static List<RdapEntityRecord> parseEntities(List<Object> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<RdapEntityRecord> list = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> entityMap = (Map<String, Object>) m;
                String handle = getString(entityMap, "handle");
                List<String> roles = getListOfStrings(entityMap, "roles");

                // Parse vCard
                String fn = null;
                String org = null;
                String email = null;
                String phone = null;
                String address = null;

                Object vcard = entityMap.get("vcardArray");
                if (vcard instanceof List<?> vcardList && vcardList.size() >= 2) {
                    if (vcardList.get(1) instanceof List<?> properties) {
                        for (Object propObj : properties) {
                            if (propObj instanceof List<?> prop && prop.size() >= 4) {
                                String propName = String.valueOf(prop.get(0)).toLowerCase(Locale.ROOT);
                                Object val = prop.get(3);
                                switch (propName) {
                                    case "fn" -> fn = formatVcardString(val);
                                    case "org" -> org = formatVcardString(val);
                                    case "email" -> email = formatVcardString(val);
                                    case "tel" -> phone = formatVcardString(val);
                                    case "adr" -> address = formatVcardAddress(val);
                                    default -> {}
                                }
                            }
                        }
                    }
                }

                list.add(new RdapEntityRecord(handle, roles, fn, org, email, phone, address));

                // Recursively parse child entities if present
                if (entityMap.containsKey("entities")) {
                    list.addAll(parseEntities((List<Object>) entityMap.get("entities")));
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static String formatVcardString(Object val) {
        if (val == null) return null;
        if (val instanceof List<?> l) {
            StringJoiner sj = new StringJoiner(" ");
            for (Object o : l) {
                if (o != null && !o.toString().isBlank()) {
                    sj.add(o.toString().trim());
                }
            }
            return sj.toString();
        }
        return val.toString().trim();
    }

    private static String formatVcardAddress(Object val) {
        if (val == null) return null;
        if (val instanceof List<?> l) {
            // [postOfficeBox, extendedAddress, streetAddress, locality, region, postalCode, countryName]
            StringJoiner sj = new StringJoiner(", ");
            for (Object part : l) {
                if (part != null && !part.toString().isBlank()) {
                    sj.add(part.toString().trim());
                }
            }
            return sj.length() > 0 ? sj.toString() : null;
        }
        return val.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Instant> parseEvents(List<Object> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Instant> map = new LinkedHashMap<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> em = (Map<String, Object>) m;
                String action = getString(em, "eventAction");
                String dateStr = getString(em, "eventDate");
                if (action != null && dateStr != null) {
                    try {
                        Instant instant = DateTimeFormatter.ISO_DATE_TIME.parse(dateStr, Instant::from);
                        map.put(action, instant);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseNotices(List<Object> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> nm = (Map<String, Object>) m;
                String title = getString(nm, "title");
                if (title != null && !title.isBlank()) {
                    list.add(title);
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    private static long getLong(Map<String, Object> map, String key, long def) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.longValue();
        } else if (val != null) {
            try {
                return Long.parseLong(val.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getListOfStrings(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> l) {
            List<String> res = new ArrayList<>();
            for (Object item : l) {
                if (item != null) {
                    res.add(item.toString().trim());
                }
            }
            return Collections.unmodifiableList(res);
        }
        return Collections.emptyList();
    }
}
