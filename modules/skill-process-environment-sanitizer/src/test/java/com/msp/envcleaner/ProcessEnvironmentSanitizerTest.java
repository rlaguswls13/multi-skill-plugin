package com.msp.envcleaner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProcessEnvironmentSanitizer Tests")
class ProcessEnvironmentSanitizerTest {

    @Nested
    @DisplayName("Default Scrubbing Behavior")
    class DefaultScrubbingTests {

        @Test
        @DisplayName("Scrubs sensitive environment variables while keeping safe ones")
        void scrubsSensitiveVariables() {
            ProcessEnvironmentSanitizer sanitizer = ProcessEnvironmentSanitizer.defaultSanitizer();

            Map<String, String> env = Map.of(
                    "PATH", "/usr/bin:/bin",
                    "JAVA_HOME", "/opt/jdk-21",
                    "DATABASE_PASSWORD", "super_secret_123",
                    "AWS_SECRET_ACCESS_KEY", "AKIAIOSFODNN7EXAMPLE",
                    "STRIPE_API_KEY", "sk_live_12345",
                    "JWT_AUTH_TOKEN", "eyJhbGciOi...",
                    "APP_PORT", "8080"
            );

            Map<String, String> clean = sanitizer.sanitize(env);

            assertEquals(3, clean.size());
            assertEquals("/usr/bin:/bin", clean.get("PATH"));
            assertEquals("/opt/jdk-21", clean.get("JAVA_HOME"));
            assertEquals("8080", clean.get("APP_PORT"));

            assertFalse(clean.containsKey("DATABASE_PASSWORD"));
            assertFalse(clean.containsKey("AWS_SECRET_ACCESS_KEY"));
            assertFalse(clean.containsKey("STRIPE_API_KEY"));
            assertFalse(clean.containsKey("JWT_AUTH_TOKEN"));
        }

        @Test
        @DisplayName("Whitelist overrides sensitive pattern matches")
        void whitelistOverridesPattern() {
            ProcessEnvironmentSanitizer sanitizer = ProcessEnvironmentSanitizer.builder()
                    .addWhitelist("SAFE_KEYSTORE_PATH")
                    .build();

            Map<String, String> env = Map.of(
                    "SAFE_KEYSTORE_PATH", "/etc/ssl/keystore.jks",
                    "PRIVATE_KEY_DATA", "MIIEvgIBADANBg..."
            );

            Map<String, String> clean = sanitizer.sanitize(env);
            assertTrue(clean.containsKey("SAFE_KEYSTORE_PATH"));
            assertFalse(clean.containsKey("PRIVATE_KEY_DATA"));
        }
    }

    @Nested
    @DisplayName("Masking / Redaction Mode")
    class MaskingModeTests {

        @Test
        @DisplayName("Masks sensitive variables with custom replacement string")
        void masksSensitiveVariables() {
            ProcessEnvironmentSanitizer sanitizer = ProcessEnvironmentSanitizer.builder()
                    .actionOnSensitive(SanitizeAction.MASK)
                    .maskReplacement("[CONFIDENTIAL]")
                    .build();

            Map<String, String> env = Map.of(
                    "DB_PASSWORD", "tiger",
                    "SERVICE_NAME", "order-service"
            );

            Map<String, String> masked = sanitizer.sanitize(env);
            assertEquals("[CONFIDENTIAL]", masked.get("DB_PASSWORD"));
            assertEquals("order-service", masked.get("SERVICE_NAME"));
        }
    }

    @Nested
    @DisplayName("Audit Report and Logging")
    class AuditReportTests {

        @Test
        @DisplayName("Generates audit report with action counts and entry explanations")
        void auditReport() {
            ProcessEnvironmentSanitizer sanitizer = ProcessEnvironmentSanitizer.defaultSanitizer();

            Map<String, String> env = Map.of(
                    "PATH", "/usr/bin",
                    "SECRET_TOKEN", "xyz999",
                    "SERVER_NAME", "prod-web-01"
            );

            SanitizationReport report = sanitizer.audit(env);

            assertEquals(1, report.scrubbedCount());
            assertEquals(2, report.preservedCount());
            assertEquals(0, report.maskedCount());

            SanitizationReport.AuditEntry secretEntry = report.auditLog().stream()
                    .filter(e -> e.variableName().equals("SECRET_TOKEN"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(SanitizeAction.REMOVE, secretEntry.action());
            assertTrue(secretEntry.reason().contains("Matched sensitive pattern"));
        }

        @Test
        @DisplayName("Handles null and empty environment maps safely")
        void emptyEnvironment() {
            ProcessEnvironmentSanitizer sanitizer = ProcessEnvironmentSanitizer.defaultSanitizer();
            SanitizationReport rNull = sanitizer.audit(null);
            assertTrue(rNull.sanitizedEnvironment().isEmpty());
            assertEquals(0, rNull.scrubbedCount());

            SanitizationReport rEmpty = sanitizer.audit(Map.of());
            assertTrue(rEmpty.sanitizedEnvironment().isEmpty());
        }
    }

    @Nested
    @DisplayName("ProcessBuilder Integration")
    class ProcessBuilderIntegrationTests {

        @Test
        @DisplayName("Applies sanitization to ProcessBuilder environment")
        void appliesToProcessBuilder() {
            ProcessEnvironmentSanitizer sanitizer = ProcessEnvironmentSanitizer.defaultSanitizer();
            ProcessBuilder pb = new ProcessBuilder("echo", "hello");

            pb.environment().put("MY_SECRET_KEY", "sensitive-val");
            pb.environment().put("SAFE_CONFIG_FLAG", "true");

            sanitizer.applyTo(pb);

            assertFalse(pb.environment().containsKey("MY_SECRET_KEY"));
            assertEquals("true", pb.environment().get("SAFE_CONFIG_FLAG"));
        }
    }
}
