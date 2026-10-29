package com.msp.ini;

import java.util.*;

/**
 * Representation of an INI configuration file containing optional global properties
 * and named sections with key-value pairs.
 */
public final class IniFile {

    private final Map<String, String> globalProperties;
    private final Map<String, Map<String, String>> sections;

    public IniFile() {
        this.globalProperties = new LinkedHashMap<>();
        this.sections = new LinkedHashMap<>();
    }

    public IniFile(Map<String, String> globalProperties, Map<String, Map<String, String>> sections) {
        this.globalProperties = new LinkedHashMap<>(globalProperties != null ? globalProperties : Map.of());
        this.sections = new LinkedHashMap<>();
        if (sections != null) {
            for (Map.Entry<String, Map<String, String>> entry : sections.entrySet()) {
                this.sections.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
            }
        }
    }

    public Map<String, String> getGlobalProperties() {
        return Collections.unmodifiableMap(globalProperties);
    }

    public Set<String> getSections() {
        return Collections.unmodifiableSet(sections.keySet());
    }

    public boolean hasSection(String section) {
        return section != null && sections.containsKey(section);
    }

    public Map<String, String> getSection(String section) {
        if (section == null || !sections.containsKey(section)) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(sections.get(section));
    }

    public boolean contains(String section, String key) {
        if (section == null || section.isEmpty()) {
            return globalProperties.containsKey(key);
        }
        Map<String, String> sec = sections.get(section);
        return sec != null && sec.containsKey(key);
    }

    public boolean contains(String key) {
        return globalProperties.containsKey(key);
    }

    // --- String Getters ---

    public String get(String section, String key) {
        if (section == null || section.isEmpty()) {
            return globalProperties.get(key);
        }
        Map<String, String> sec = sections.get(section);
        return sec != null ? sec.get(key) : null;
    }

    public String get(String section, String key, String defaultValue) {
        String val = get(section, key);
        return val != null ? val : defaultValue;
    }

    public String get(String key) {
        return globalProperties.get(key);
    }

    public String getOrDefault(String key, String defaultValue) {
        String val = get(key);
        return val != null ? val : defaultValue;
    }

    // --- Typed Getters ---

    public Integer getInt(String section, String key) {
        String val = get(section, key);
        if (val == null) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new IniException("Cannot parse integer for section [" + section + "], key '" + key + "': " + val, e);
        }
    }

    public int getInt(String section, String key, int defaultValue) {
        Integer val = getInt(section, key);
        return val != null ? val : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        return getInt("", key, defaultValue);
    }

    public Long getLong(String section, String key) {
        String val = get(section, key);
        if (val == null) return null;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            throw new IniException("Cannot parse long for section [" + section + "], key '" + key + "': " + val, e);
        }
    }

    public long getLong(String section, String key, long defaultValue) {
        Long val = getLong(section, key);
        return val != null ? val : defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        return getLong("", key, defaultValue);
    }

    public Double getDouble(String section, String key) {
        String val = get(section, key);
        if (val == null) return null;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            throw new IniException("Cannot parse double for section [" + section + "], key '" + key + "': " + val, e);
        }
    }

    public double getDouble(String section, String key, double defaultValue) {
        Double val = getDouble(section, key);
        return val != null ? val : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        return getDouble("", key, defaultValue);
    }

    public Boolean getBoolean(String section, String key) {
        String val = get(section, key);
        if (val == null) return null;
        String s = val.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s)) {
            return Boolean.TRUE;
        }
        if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "off".equals(s)) {
            return Boolean.FALSE;
        }
        throw new IniException("Cannot parse boolean for section [" + section + "], key '" + key + "': " + val);
    }

    public boolean getBoolean(String section, String key, boolean defaultValue) {
        Boolean val = getBoolean(section, key);
        return val != null ? val : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean("", key, defaultValue);
    }

    // --- Setters ---

    public IniFile set(String section, String key, Object value) {
        if (key == null) return this;
        String strVal = value != null ? String.valueOf(value) : "";
        if (section == null || section.isEmpty()) {
            globalProperties.put(key, strVal);
        } else {
            sections.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(key, strVal);
        }
        return this;
    }

    public IniFile set(String key, Object value) {
        return set("", key, value);
    }

    public String toIni() {
        return IniWriter.write(this);
    }

    @Override
    public String toString() {
        return toIni();
    }
}
