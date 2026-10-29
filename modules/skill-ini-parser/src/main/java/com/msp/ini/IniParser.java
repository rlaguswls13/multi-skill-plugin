package com.msp.ini;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Robust parser for Windows and Linux INI configuration files.
 * Supports sections, global properties, inline comments (; and #), and quoted values.
 */
public final class IniParser {

    private IniParser() {}

    /**
     * Parses an INI configuration from a string.
     */
    public static IniFile parse(String text) {
        if (text == null || text.isBlank()) {
            return new IniFile();
        }
        return parse(new StringReader(text));
    }

    /**
     * Parses an INI configuration from a file path.
     */
    public static IniFile parse(Path path) {
        if (path == null || !Files.exists(path)) {
            throw new IniException("File not found: " + path);
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new IniException("Failed to read INI file from " + path, e);
        }
    }

    /**
     * Parses an INI configuration from a Reader.
     */
    public static IniFile parse(Reader reader) {
        Map<String, String> globals = new LinkedHashMap<>();
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();

        String currentSection = null;
        int lineNumber = 0;

        try (BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                    continue;
                }

                // Check section header [section]
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    currentSection = trimmed.substring(1, trimmed.length() - 1).trim();
                    if (currentSection.isEmpty()) {
                        throw new IniException("Empty section name in header", lineNumber);
                    }
                    sections.computeIfAbsent(currentSection, k -> new LinkedHashMap<>());
                    continue;
                }

                // Strip inline comment
                String stripped = stripInlineComment(trimmed);
                if (stripped.isEmpty()) {
                    continue;
                }

                int sep = findSeparator(stripped);
                if (sep <= 0) {
                    throw new IniException("Invalid line without '=' or ':' key-value delimiter: " + trimmed, lineNumber);
                }

                String key = stripped.substring(0, sep).trim();
                String rawVal = stripped.substring(sep + 1).trim();
                String val = unquote(rawVal);

                if (currentSection == null) {
                    globals.put(key, val);
                } else {
                    sections.get(currentSection).put(key, val);
                }
            }
        } catch (IOException e) {
            throw new IniException("Error reading INI stream at line " + lineNumber, lineNumber, e);
        }

        return new IniFile(globals, sections);
    }

    private static int findSeparator(String line) {
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (!inSingle && !inDouble && (c == '=' || c == ':')) {
                return i;
            }
        }
        return -1;
    }

    private static String stripInlineComment(String line) {
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (!inSingle && !inDouble && (c == ';' || c == '#')) {
                if (i == 0 || Character.isWhitespace(line.charAt(i - 1))) {
                    return line.substring(0, i).trim();
                }
            }
        }
        return line.trim();
    }

    private static String unquote(String val) {
        if (val.length() >= 2) {
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                return val.substring(1, val.length() - 1);
            }
        }
        return val;
    }
}
