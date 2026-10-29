package com.msp.ini;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IniParser & IniWriter Tests")
class IniParserTest {

    @Test
    @DisplayName("Should parse global properties and multiple sections with comments")
    void testBasicIniParsing() {
        String iniContent = """
            ; Global configuration
            app_name = SuperApp
            version = 1.0.0 # inline version comment

            [database]
            host = 192.168.1.100
            port = 5432
            enabled = true
            timeout = 30.5

            [cache]
            type = redis
            max_memory = 1073741824 ; 1GB
            """;

        IniFile ini = IniParser.parse(iniContent);

        // Global properties
        assertEquals("SuperApp", ini.get("app_name"));
        assertEquals("1.0.0", ini.get("version"));
        assertTrue(ini.contains("app_name"));

        // Sections
        assertEquals(Set.of("database", "cache"), ini.getSections());
        assertTrue(ini.hasSection("database"));
        assertTrue(ini.hasSection("cache"));

        // Database section
        assertEquals("192.168.1.100", ini.get("database", "host"));
        assertEquals(5432, ini.getInt("database", "port", 0));
        assertTrue(ini.getBoolean("database", "enabled", false));
        assertEquals(30.5, ini.getDouble("database", "timeout", 0.0));

        // Cache section
        assertEquals("redis", ini.get("cache", "type"));
        assertEquals(1073741824L, ini.getLong("cache", "max_memory", 0L));
    }

    @Test
    @DisplayName("Should handle colon delimiter and quoted strings with special chars")
    void testColonAndQuotes() {
        String iniContent = """
            [web]
            listen: "0.0.0.0:8080"
            motd: 'Welcome; feel free to #explore!'
            """;

        IniFile ini = IniParser.parse(iniContent);
        assertEquals("0.0.0.0:8080", ini.get("web", "listen"));
        assertEquals("Welcome; feel free to #explore!", ini.get("web", "motd"));
    }

    @Test
    @DisplayName("Should return defaults for missing sections and keys")
    void testDefaults() {
        IniFile ini = new IniFile();
        assertEquals("default_val", ini.get("missing_sec", "key", "default_val"));
        assertEquals(8080, ini.getInt("missing_sec", "port", 8080));
        assertTrue(ini.getBoolean("missing_sec", "flag", true));
        assertEquals(123456L, ini.getLong("missing_sec", "long", 123456L));
        assertEquals(3.14, ini.getDouble("missing_sec", "double", 3.14));
    }

    @Test
    @DisplayName("Should write and round-trip serialize IniFile")
    void testWriteAndRoundTrip() {
        IniFile original = new IniFile();
        original.set("environment", "production");
        original.set("server", "host", "api.msp.internal");
        original.set("server", "port", 443);
        original.set("server", "ssl", true);

        String text = original.toIni();
        assertNotNull(text);

        IniFile parsed = IniParser.parse(text);
        assertEquals("production", parsed.get("environment"));
        assertEquals("api.msp.internal", parsed.get("server", "host"));
        assertEquals(443, parsed.getInt("server", "port", 0));
        assertTrue(parsed.getBoolean("server", "ssl", false));
    }

    @Test
    @DisplayName("Should fail on malformed INI content")
    void testMalformedIni() {
        String badHeader = "[]\nkey=val";
        assertThrows(IniException.class, () -> IniParser.parse(badHeader));

        String noDelimiter = "[sec]\ninvalid_line_without_equals";
        assertThrows(IniException.class, () -> IniParser.parse(noDelimiter));
    }
}
