package com.msp.props;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PropertiesLoader Tests")
class PropertiesLoaderTest {

    @Test
    @DisplayName("Should load .env and .properties with correct merge precedence")
    void testMergePrecedence() {
        String baseProperties = """
            app.name=BaseApp
            app.port=8080
            app.timeout=3000
            feature.beta=false
            """;

        String dotEnvOverrides = """
            # Environment overrides
            app.port=9090
            export feature.beta=true
            NEW_SETTING="loaded from .env"
            """;

        PropertiesLoader loader = PropertiesLoader.builder()
                .loadProperties(baseProperties)
                .loadDotEnv(dotEnvOverrides)
                .set("app.port", "9999") // highest precedence via direct set
                .build();

        assertEquals("BaseApp", loader.getString("app.name"));
        assertEquals(9999, loader.getInt("app.port"));
        assertEquals(3000, loader.getInt("app.timeout"));
        assertTrue(loader.getBoolean("feature.beta"));
        assertEquals("loaded from .env", loader.getString("NEW_SETTING"));
    }

    @Test
    @DisplayName("Should expand variables within properties and with fallbacks")
    void testVariableExpansion() {
        String config = """
            SERVER_HOST=127.0.0.1
            SERVER_PORT=8000
            BASE_URL=http://${SERVER_HOST}:${SERVER_PORT}/api
            AUTH_TIMEOUT=${TIMEOUT:-5000}
            DEBUG_MODE=${APP_DEBUG:false}
            FALLBACK_DEFAULT=${UNSET_KEY:-default_value}
            """;

        PropertiesLoader loader = PropertiesLoader.builder()
                .loadProperties(config)
                .build();

        assertEquals("http://127.0.0.1:8000/api", loader.getString("BASE_URL"));
        assertEquals(5000, loader.getInt("AUTH_TIMEOUT"));
        assertFalse(loader.getBoolean("DEBUG_MODE"));
        assertEquals("default_value", loader.getString("FALLBACK_DEFAULT"));
    }

    @Test
    @DisplayName("Should expand system environment variables when enabled")
    void testEnvExpansion() {
        String config = """
            DATABASE_URL=jdbc:postgresql://${DB_HOST:-localhost}:${DB_PORT:-5432}/${DB_NAME}
            """;

        PropertiesLoader loader = PropertiesLoader.builder()
                .loadDotEnv(config)
                .enableEnvExpansion()
                .customLookup(key -> switch (key) {
                    case "DB_HOST" -> "postgres.internal";
                    case "DB_NAME" -> "production_db";
                    default -> null;
                })
                .build();

        // DB_HOST was provided by lookup, DB_PORT fell back to 5432, DB_NAME was provided by lookup
        assertEquals("jdbc:postgresql://postgres.internal:5432/production_db", loader.getString("DATABASE_URL"));
    }

    @Test
    @DisplayName("Should parse typed values correctly")
    void testTypedGetters() {
        String config = """
            count=100
            max_limit=999999999999
            ratio=0.75
            flag_on=on
            flag_yes=yes
            flag_1=1
            flag_off=off
            flag_0=0
            """;

        PropertiesLoader loader = PropertiesLoader.builder()
                .loadProperties(config)
                .build();

        assertEquals(100, loader.getInt("count"));
        assertEquals(999999999999L, loader.getLong("max_limit"));
        assertEquals(0.75, loader.getDouble("ratio"));
        assertTrue(loader.getBoolean("flag_on"));
        assertTrue(loader.getBoolean("flag_yes"));
        assertTrue(loader.getBoolean("flag_1"));
        assertFalse(loader.getBoolean("flag_off"));
        assertFalse(loader.getBoolean("flag_0"));

        assertEquals(50, loader.getInt("missing_int", 50));
        assertEquals("fallback", loader.getString("missing_str", "fallback"));
    }

    @Test
    @DisplayName("Should handle quoted strings and escape sequences in .env")
    void testDotEnvQuotes() {
        String dotEnv = """
            SIMPLE=hello
            DOUBLE_QUOTED="hello \\n world"
            SINGLE_QUOTED='hello $not_expanded world'
            WITH_INLINE_COMMENT=running # this is comment
            """;

        PropertiesLoader loader = PropertiesLoader.builder()
                .loadDotEnv(dotEnv)
                .build();

        assertEquals("hello", loader.getString("SIMPLE"));
        assertEquals("hello \n world", loader.getString("DOUBLE_QUOTED"));
        assertEquals("hello $not_expanded world", loader.getString("SINGLE_QUOTED"));
        assertEquals("running", loader.getString("WITH_INLINE_COMMENT"));
    }

    @Test
    @DisplayName("Should throw exception on invalid type conversion")
    void testTypeConversionFailure() {
        PropertiesLoader loader = PropertiesLoader.builder()
                .set("bad_int", "not_a_number")
                .set("bad_bool", "maybe")
                .build();

        assertThrows(PropertiesLoaderException.class, () -> loader.getInt("bad_int"));
        assertThrows(PropertiesLoaderException.class, () -> loader.getBoolean("bad_bool"));
    }
}
