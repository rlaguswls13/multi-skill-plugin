package com.msp.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Validator Fluent Builder Tests")
class ValidatorTest {

    record UserDto(String username, String email, String website, int age, List<String> tags) {}

    @Test
    @DisplayName("Valid DTO should produce valid result")
    void testValidDto() {
        Validator<UserDto> validator = Validator.<UserDto>builder()
                .notBlank("username", UserDto::username, "Username is required")
                .email("email", UserDto::email, "Invalid email format")
                .url("website", UserDto::website, "Invalid website URL")
                .range("age", UserDto::age, 18, 100, "Age must be between 18 and 100")
                .notEmpty("tags", UserDto::tags, "At least one tag is required")
                .build();

        UserDto validUser = new UserDto("johndoe", "john@example.com", "https://johndoe.dev", 25, List.of("developer"));
        ValidationResult result = validator.validate(validUser);

        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        assertTrue(result.errors().isEmpty());
        assertDoesNotThrow(() -> validator.validateAndThrow(validUser));
    }

    @Test
    @DisplayName("Invalid DTO should collect all validation errors")
    void testInvalidDto() {
        Validator<UserDto> validator = Validator.<UserDto>builder()
                .notBlank("username", UserDto::username, "Username is required")
                .email("email", UserDto::email, "Invalid email format")
                .range("age", UserDto::age, 18, 100, "Age must be between 18 and 100")
                .build();

        UserDto invalidUser = new UserDto("", "invalid-email", "not-a-url", 15, List.of());
        ValidationResult result = validator.validate(invalidUser);

        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertEquals(3, result.errors().size());

        assertEquals("username", result.errors().get(0).field());
        assertEquals("email", result.errors().get(1).field());
        assertEquals("age", result.errors().get(2).field());

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validateAndThrow(invalidUser));
        assertTrue(ex.getMessage().contains("Validation failed with 3 error(s)"));
        assertEquals(3, ex.getErrors().size());
    }

    @Test
    @DisplayName("Null target object validation")
    void testNullTarget() {
        Validator<UserDto> validator = Validator.<UserDto>builder()
                .notBlank("username", UserDto::username, "Username is required")
                .build();

        ValidationResult result = validator.validate(null);
        assertTrue(result.hasErrors());
        assertEquals("target", result.errors().getFirst().field());
    }

    @Test
    @DisplayName("Custom rule and check predicate")
    void testCustomPredicate() {
        Validator<String> validator = Validator.<String>builder()
                .check(s -> s.startsWith("MSP-"), "Must start with prefix MSP-")
                .length("code", s -> s, 5, 10, "Length must be between 5 and 10")
                .build();

        assertTrue(validator.validate("MSP-999").isValid());

        ValidationResult invalidResult = validator.validate("X");
        assertFalse(invalidResult.isValid());
        assertEquals(2, invalidResult.errors().size());
    }

    @Test
    @DisplayName("Pattern and IPv4/IPv6 validator rules")
    void testNetworkRules() {
        record ServerConfig(String ip, String version) {}

        Validator<ServerConfig> validator = Validator.<ServerConfig>builder()
                .ipv4("ip", ServerConfig::ip, "Must be valid IPv4")
                .pattern("version", ServerConfig::version, "^v\\d+$", "Must match v{number}")
                .build();

        assertTrue(validator.validate(new ServerConfig("10.0.0.1", "v2")).isValid());
        assertFalse(validator.validate(new ServerConfig("999.0.0.1", "beta")).isValid());
    }
}
