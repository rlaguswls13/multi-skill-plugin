package com.msp.props;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hierarchical .env and .properties configuration loader with
 * environment variable expansion and type-safe getters.
 */
public final class PropertiesLoader {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    private final Map<String, String> properties;

    private PropertiesLoader(Map<String, String> properties) {
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /**
     * Creates a new builder for configuring and loading properties.
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getString(String key) {
        return properties.get(key);
    }

    public String getString(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public Optional<String> getOptional(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    public Integer getInt(String key) {
        String val = properties.get(key);
        if (val == null) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new PropertiesLoaderException("Key '" + key + "' with value '" + val + "' cannot be parsed as Integer", e);
        }
    }

    public int getInt(String key, int defaultValue) {
        Integer val = getInt(key);
        return val != null ? val : defaultValue;
    }

    public Long getLong(String key) {
        String val = properties.get(key);
        if (val == null) return null;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            throw new PropertiesLoaderException("Key '" + key + "' with value '" + val + "' cannot be parsed as Long", e);
        }
    }

    public long getLong(String key, long defaultValue) {
        Long val = getLong(key);
        return val != null ? val : defaultValue;
    }

    public Double getDouble(String key) {
        String val = properties.get(key);
        if (val == null) return null;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            throw new PropertiesLoaderException("Key '" + key + "' with value '" + val + "' cannot be parsed as Double", e);
        }
    }

    public double getDouble(String key, double defaultValue) {
        Double val = getDouble(key);
        return val != null ? val : defaultValue;
    }

    public Boolean getBoolean(String key) {
        String val = properties.get(key);
        if (val == null) return null;
        String s = val.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s)) {
            return Boolean.TRUE;
        }
        if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "off".equals(s)) {
            return Boolean.FALSE;
        }
        throw new PropertiesLoaderException("Key '" + key + "' with value '" + val + "' cannot be parsed as Boolean");
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Boolean val = getBoolean(key);
        return val != null ? val : defaultValue;
    }

    public boolean contains(String key) {
        return properties.containsKey(key);
    }

    public int size() {
        return properties.size();
    }

    public Map<String, String> getAll() {
        return properties;
    }

    /**
     * Builder for chaining sources and configuring variable expansion.
     */
    public static final class Builder {
        private final Map<String, String> accumulated = new LinkedHashMap<>();
        private boolean envExpansion = false;
        private boolean systemProperties = false;
        private Function<String, String> customLookup = null;

        public Builder loadDotEnv(Path path) {
            if (path != null && Files.exists(path)) {
                try {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    return loadDotEnvContent(content);
                } catch (IOException e) {
                    throw new PropertiesLoaderException("Failed to read .env file at " + path, e);
                }
            }
            return this;
        }

        public Builder loadDotEnv(String content) {
            return loadDotEnvContent(content);
        }

        private Builder loadDotEnvContent(String content) {
            if (content == null || content.isBlank()) {
                return this;
            }
            String[] lines = content.split("\r?\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring(7).trim();
                }

                int eqIdx = trimmed.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, eqIdx).trim();
                String rawVal = trimmed.substring(eqIdx + 1).trim();

                String value = parseDotEnvValue(rawVal);
                accumulated.put(key, value);
            }
            return this;
        }

        private String parseDotEnvValue(String raw) {
            if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
                String inside = raw.substring(1, raw.length() - 1);
                return inside.replace("\\n", "\n")
                             .replace("\\r", "\r")
                             .replace("\\t", "\t")
                             .replace("\\\"", "\"")
                             .replace("\\\\", "\\");
            }
            if (raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2) {
                return raw.substring(1, raw.length() - 1);
            }
            // Strip inline comment if outside quotes
            int commentIdx = -1;
            for (int i = 0; i < raw.length(); i++) {
                if (raw.charAt(i) == '#' && (i == 0 || Character.isWhitespace(raw.charAt(i - 1)))) {
                    commentIdx = i;
                    break;
                }
            }
            if (commentIdx != -1) {
                return raw.substring(0, commentIdx).trim();
            }
            return raw;
        }

        public Builder loadProperties(Path path) {
            if (path != null && Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    return loadProperties(in);
                } catch (IOException e) {
                    throw new PropertiesLoaderException("Failed to read properties file at " + path, e);
                }
            }
            return this;
        }

        public Builder loadProperties(InputStream in) {
            if (in == null) return this;
            Properties props = new Properties();
            try {
                props.load(in);
                for (String name : props.stringPropertyNames()) {
                    accumulated.put(name, props.getProperty(name));
                }
            } catch (IOException e) {
                throw new PropertiesLoaderException("Failed to parse properties input stream", e);
            }
            return this;
        }

        public Builder loadProperties(String content) {
            if (content == null || content.isBlank()) return this;
            return loadProperties(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }

        public Builder loadMap(Map<String, String> map) {
            if (map != null) {
                accumulated.putAll(map);
            }
            return this;
        }

        public Builder set(String key, String value) {
            if (key != null && value != null) {
                accumulated.put(key, value);
            }
            return this;
        }

        public Builder enableEnvExpansion() {
            this.envExpansion = true;
            return this;
        }

        public Builder enableEnvExpansion(boolean enable) {
            this.envExpansion = enable;
            return this;
        }

        public Builder enableSystemProperties() {
            this.systemProperties = true;
            return this;
        }

        /**
         * For unit testing environment variable expansion without modifying system state.
         */
        public Builder customLookup(Function<String, String> lookup) {
            this.customLookup = lookup;
            return this;
        }

        public PropertiesLoader build() {
            Map<String, String> resolved = new LinkedHashMap<>();
            // First copy all
            resolved.putAll(accumulated);

            // Expand placeholders iteratively
            for (int pass = 0; pass < 5; pass++) {
                boolean anyChanged = false;
                for (Map.Entry<String, String> entry : resolved.entrySet()) {
                    String original = entry.getValue();
                    String expanded = expandValue(original, resolved);
                    if (!Objects.equals(original, expanded)) {
                        entry.setValue(expanded);
                        anyChanged = true;
                    }
                }
                if (!anyChanged) break;
            }

            return new PropertiesLoader(resolved);
        }

        private String expandValue(String text, Map<String, String> context) {
            if (text == null || !text.contains("${")) {
                return text;
            }

            Matcher matcher = VAR_PATTERN.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String expr = matcher.group(1);
                String varName = expr;
                String fallback = null;

                if (expr.contains(":-")) {
                    int idx = expr.indexOf(":-");
                    varName = expr.substring(0, idx).trim();
                    fallback = expr.substring(idx + 2);
                } else if (expr.contains(":")) {
                    int idx = expr.indexOf(":");
                    varName = expr.substring(0, idx).trim();
                    fallback = expr.substring(idx + 1);
                }

                String val = lookupVariable(varName, context);
                if (val != null && !val.isEmpty()) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(val));
                } else if (fallback != null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(fallback));
                } else if (val != null) {
                    matcher.appendReplacement(sb, "");
                } else {
                    // Leave unreplaced or empty
                    matcher.appendReplacement(sb, "");
                }
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        private String lookupVariable(String name, Map<String, String> context) {
            // 1. Current configuration context
            if (context.containsKey(name)) {
                return context.get(name);
            }
            // 2. Custom lookup function (for test isolation)
            if (customLookup != null) {
                String val = customLookup.apply(name);
                if (val != null) return val;
            }
            // 3. System Environment Variables
            if (envExpansion) {
                String val = System.getenv(name);
                if (val != null) return val;
            }
            // 4. System Properties
            if (systemProperties || envExpansion) {
                String val = System.getProperty(name);
                if (val != null) return val;
            }
            return null;
        }
    }
}
