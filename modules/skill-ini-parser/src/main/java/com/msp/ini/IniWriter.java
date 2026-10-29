package com.msp.ini;

import java.util.Map;

/**
 * Serializer for writing {@link IniFile} instances to standard INI configuration format.
 */
public final class IniWriter {

    private IniWriter() {}

    /**
     * Serializes an IniFile into a formatted INI string.
     *
     * @param ini the IniFile to serialize
     * @return formatted INI string
     */
    public static String write(IniFile ini) {
        if (ini == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 1. Write global properties
        Map<String, String> globals = ini.getGlobalProperties();
        if (!globals.isEmpty()) {
            for (Map.Entry<String, String> entry : globals.entrySet()) {
                sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
        }

        // 2. Write sections
        boolean firstSection = true;
        for (String sectionName : ini.getSections()) {
            if (!globals.isEmpty() || !firstSection) {
                sb.append("\n");
            }
            firstSection = false;

            sb.append("[").append(sectionName).append("]\n");
            Map<String, String> secEntries = ini.getSection(sectionName);
            for (Map.Entry<String, String> entry : secEntries.entrySet()) {
                sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
        }

        return sb.toString();
    }
}
