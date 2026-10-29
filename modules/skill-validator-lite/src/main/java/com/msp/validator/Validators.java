package com.msp.validator;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Common validation utilities for strings, numbers, formats, and collections.
 */
public final class Validators {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,10}$"
    );

    private Validators() {
    }

    public static boolean isBlank(CharSequence cs) {
        return cs == null || cs.toString().isBlank();
    }

    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.isEmpty();
    }

    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> coll) {
        return !isEmpty(coll);
    }

    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    public static boolean isEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null || uri.getHost().isBlank()) {
                return false;
            }
            String scheme = uri.getScheme().toLowerCase();
            return scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isIpv4(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String[] parts = ip.trim().split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            if (part.length() > 1 && part.startsWith("0")) {
                return false; // Disallow leading zeros (e.g. 01)
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            int val = Integer.parseInt(part);
            if (val < 0 || val > 255) {
                return false;
            }
        }
        return true;
    }

    public static boolean isIpv6(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String trimmed = ip.trim();
        if (trimmed.equals("::")) {
            return true;
        }
        int doubleColonIndex = trimmed.indexOf("::");
        if (doubleColonIndex != -1) {
            if (trimmed.indexOf("::", doubleColonIndex + 2) != -1) {
                return false; // only one "::" allowed
            }
            String left = trimmed.substring(0, doubleColonIndex);
            String right = trimmed.substring(doubleColonIndex + 2);
            String[] leftParts = left.isEmpty() ? new String[0] : left.split(":", -1);
            String[] rightParts = right.isEmpty() ? new String[0] : right.split(":", -1);
            if (leftParts.length + rightParts.length > 7) {
                return false;
            }
            for (String p : leftParts) {
                if (!isValidHexHextet(p)) {
                    return false;
                }
            }
            for (String p : rightParts) {
                if (!isValidHexHextet(p)) {
                    return false;
                }
            }
            return true;
        } else {
            String[] parts = trimmed.split(":", -1);
            if (parts.length != 8) {
                return false;
            }
            for (String p : parts) {
                if (!isValidHexHextet(p)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean isValidHexHextet(String part) {
        if (part == null || part.isEmpty() || part.length() > 4) {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }

    public static boolean matches(String text, Pattern pattern) {
        if (text == null || pattern == null) {
            return false;
        }
        return pattern.matcher(text).matches();
    }

    public static boolean matches(String text, String regex) {
        if (text == null || regex == null) {
            return false;
        }
        return text.matches(regex);
    }

    public static <T extends Comparable<T>> boolean inRange(T value, T min, T max) {
        if (value == null) {
            return false;
        }
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        if (max != null && value.compareTo(max) > 0) {
            return false;
        }
        return true;
    }

    public static boolean hasLength(CharSequence cs, int min, int max) {
        if (cs == null) {
            return false;
        }
        int len = cs.length();
        return len >= min && len <= max;
    }
}
