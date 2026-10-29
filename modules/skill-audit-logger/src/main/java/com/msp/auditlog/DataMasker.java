package com.msp.auditlog;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy interface and default implementation for masking sensitive audit data.
 */
public interface DataMasker {

    /**
     * Masks a given value associated with the specified key name.
     *
     * @param key   the attribute or field name
     * @param value the raw object value
     * @return masked value (or original if not sensitive)
     */
    Object mask(String key, Object value);

    /**
     * Creates a default data masker with standard key names and sensitive regex patterns.
     */
    static DataMasker defaultMasker() {
        return Default.INSTANCE;
    }

    /**
     * Creates a builder to configure custom sensitive key names and patterns.
     */
    static Builder builder() {
        return new Builder();
    }

    final class Builder {
        private final Set<String> sensitiveKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        private String maskReplacement = "******";

        public Builder() {
            sensitiveKeys.addAll(Default.DEFAULT_SENSITIVE_KEYS);
        }

        public Builder addSensitiveKeys(String... keys) {
            for (String k : keys) {
                if (k != null) sensitiveKeys.add(k.trim());
            }
            return this;
        }

        public Builder maskReplacement(String replacement) {
            this.maskReplacement = Objects.requireNonNull(replacement);
            return this;
        }

        public DataMasker build() {
            return new Default(new TreeSet<>(sensitiveKeys), maskReplacement);
        }
    }

    final class Default implements DataMasker {
        private static final Set<String> DEFAULT_SENSITIVE_KEYS = Set.of(
                "password", "passwd", "pwd", "secret", "token", "accesstoken",
                "refreshtoken", "apikey", "api_key", "authorization", "auth",
                "creditcard", "cardnumber", "cvv", "cvc", "ssn", "rrn",
                "privatekey", "pin", "credential"
        );

        public static final Default INSTANCE = new Default(
                DEFAULT_SENSITIVE_KEYS,
                "******"
        );

        // Regex patterns
        private static final Pattern CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
        private static final Pattern RRN_PATTERN = Pattern.compile("\\b(\\d{6})[ -]?([1-4])\\d{6}\\b");
        private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(01[016789])[ -]?(\\d{3,4})[ -]?(\\d{4})\\b");
        private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b([a-zA-Z0-9_.+-]{1,2})([a-zA-Z0-9_.+-]+)(@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)\\b");

        private final Set<String> sensitiveKeys;
        private final String maskReplacement;

        private Default(Set<String> sensitiveKeys, String maskReplacement) {
            Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            set.addAll(sensitiveKeys);
            this.sensitiveKeys = Collections.unmodifiableSet(set);
            this.maskReplacement = maskReplacement;
        }

        @Override
        public Object mask(String key, Object value) {
            if (value == null) {
                return null;
            }

            // Check if key name is explicitly sensitive
            if (key != null && isSensitiveKey(key)) {
                return maskReplacement;
            }

            // If value is a String, check regex patterns
            if (value instanceof String str) {
                return maskStringContent(str);
            }

            // If value is nested Map
            if (value instanceof Map<?, ?> map) {
                Map<Object, Object> maskedMap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String subKey = entry.getKey() != null ? entry.getKey().toString() : "";
                    maskedMap.put(entry.getKey(), mask(subKey, entry.getValue()));
                }
                return maskedMap;
            }

            // If value is Collection
            if (value instanceof Collection<?> coll) {
                List<Object> maskedList = new ArrayList<>(coll.size());
                for (Object item : coll) {
                    maskedList.add(mask(key, item));
                }
                return maskedList;
            }

            return value;
        }

        private boolean isSensitiveKey(String key) {
            String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
            for (String sk : sensitiveKeys) {
                if (normalized.contains(sk.toLowerCase(Locale.ROOT).replaceAll("[_-]", ""))) {
                    return true;
                }
            }
            return false;
        }

        private String maskStringContent(String input) {
            String result = input;

            // 1. Mask Korean RRN: 900101-1234567 -> 900101-1****** (run before card pattern)
            Matcher rrnMatcher = RRN_PATTERN.matcher(result);
            if (rrnMatcher.find()) {
                result = rrnMatcher.replaceAll("$1-$2******");
            }

            // 2. Mask Card: keep first 4 and last 4, mask middle digits
            Matcher cardMatcher = CARD_PATTERN.matcher(result);
            if (cardMatcher.find()) {
                result = cardMatcher.replaceAll(mr -> {
                    String clean = mr.group().replaceAll("[ -]", "");
                    if (clean.length() >= 13 && clean.length() <= 16) {
                        return clean.substring(0, 4) + "-****-****-" + clean.substring(clean.length() - 4);
                    }
                    return mr.group();
                });
            }

            // 3. Mask Phone: 010-1234-5678 -> 010-****-5678
            Matcher phoneMatcher = PHONE_PATTERN.matcher(result);
            if (phoneMatcher.find()) {
                result = phoneMatcher.replaceAll("$1-****-$3");
            }

            // 4. Mask Email: minjun@gmail.com -> mi***@gmail.com
            Matcher emailMatcher = EMAIL_PATTERN.matcher(result);
            if (emailMatcher.find()) {
                result = emailMatcher.replaceAll("$1***$3");
            }

            return result;
        }
    }
}
