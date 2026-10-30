package com.msp.jsonschema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaValidatorTest {

    @Test
    @DisplayName("Valid data passes schema validation")
    void testValidData() {
        JsonSchemaValidator validator = new JsonSchemaValidator()
                .defineField("username", FieldConstraint.builder()
                        .required(true)
                        .type(JsonFieldType.STRING)
                        .minLength(3)
                        .maxLength(20)
                        .pattern("^[a-zA-Z0-9_]+$")
                        .build())
                .defineField("age", FieldConstraint.builder()
                        .required(true)
                        .type(JsonFieldType.NUMBER)
                        .min(18)
                        .max(120)
                        .build())
                .defineField("active", FieldConstraint.required(JsonFieldType.BOOLEAN))
                .defineField("tags", FieldConstraint.builder()
                        .required(false)
                        .type(JsonFieldType.ARRAY)
                        .minLength(1)
                        .maxLength(5)
                        .build())
                .defineField("profile", FieldConstraint.optional(JsonFieldType.OBJECT));

        Map<String, Object> data = new HashMap<>();
        data.put("username", "developer_42");
        data.put("age", 25);
        data.put("active", true);
        data.put("tags", List.of("java", "backend"));
        data.put("profile", Map.of("bio", "Software Engineer"));

        ValidationResult result = validator.validate(data);
        assertTrue(result.isValid());
        assertTrue(result.errorMessages().isEmpty());
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Missing required field returns error")
    void testMissingRequiredField() {
        JsonSchemaValidator validator = new JsonSchemaValidator()
                .defineField("email", FieldConstraint.required(JsonFieldType.STRING));

        Map<String, Object> data = Map.of();
        ValidationResult result = validator.validate(data);

        assertFalse(result.isValid());
        assertEquals(1, result.errorMessages().size());
        assertTrue(result.errorMessages().getFirst().contains("Required field 'email' is missing"));
    }

    @Test
    @DisplayName("Null value for required field fails")
    void testNullRequiredField() {
        JsonSchemaValidator validator = new JsonSchemaValidator()
                .defineField("email", FieldConstraint.required(JsonFieldType.STRING));

        Map<String, Object> data = new HashMap<>();
        data.put("email", null);
        ValidationResult result = validator.validate(data);

        assertFalse(result.isValid());
        assertTrue(result.errorMessages().getFirst().contains("missing or null"));
    }

    @Test
    @DisplayName("Optional field missing is allowed")
    void testOptionalFieldMissing() {
        JsonSchemaValidator validator = new JsonSchemaValidator()
                .defineField("nickname", FieldConstraint.optional(JsonFieldType.STRING));

        ValidationResult result = validator.validate(Map.of());
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Type mismatch returns error")
    void testTypeMismatch() {
        JsonSchemaValidator validator = new JsonSchemaValidator()
                .defineField("score", FieldConstraint.builder().type(JsonFieldType.NUMBER).build());

        Map<String, Object> data = Map.of("score", "one hundred");
        ValidationResult result = validator.validate(data);

        assertFalse(result.isValid());
        assertTrue(result.errorMessages().getFirst().contains("expected type NUMBER"));
    }

    @Test
    @DisplayName("String length and regex pattern constraints")
    void testStringConstraints() {
        JsonSchemaValidator validator = new JsonSchemaValidator()
                .defineField("code", FieldConstraint.builder()
                        .type(JsonFieldType.STRING)
                        .minLength(4)
                        .maxLength(6)
                        .pattern("^[A-Z]+$")
                        .build());

        // Too short
        ValidationResult r1 = validator.validate(Map.of("code", "AB"));
        assertFalse(r1.isValid());
        assertTrue(r1.errorMessages().stream().anyMatch(e -> e.contains("minimum length")));

        // Too long
        ValidationResult r2 = validator.validate(Map.of("code", "ABCDEFG"));
        assertFalse(r2.isValid());
        assertTrue(r2.errorMessages().stream().anyMatch(e -> e.contains("maximum length")));

        // Pattern mismatch (lowercase)
        ValidationResult r3 = validator.validate(Map.of("code", "abcd"));
        assertFalse(r3.isValid());
        assertTrue(r3.errorMessages().stream().anyMatch(e -> e.contains("pattern")));

        // Exact match
        ValidationResult r4 = validator.validate(Map.of("code", "ABCD"));
        assertTrue(r4.isValid());
    }

    @Test
    @DisplayName("Number min and max constraints")
    void testNumberRange() {
        JsonSchemaValidator validator = new JsonSchemaValidator()
                .defineField("rating", FieldConstraint.builder()
                        .type(JsonFieldType.NUMBER)
                        .min(1.0)
                        .max(5.0)
                        .build());

        assertTrue(validator.validate(Map.of("rating", 3)).isValid());
        assertTrue(validator.validate(Map.of("rating", 1.0)).isValid());
        assertTrue(validator.validate(Map.of("rating", 5.0)).isValid());

        ValidationResult rMin = validator.validate(Map.of("rating", 0.9));
        assertFalse(rMin.isValid());
        assertTrue(rMin.errorMessages().getFirst().contains("less than minimum"));

        ValidationResult rMax = validator.validate(Map.of("rating", 5.1));
        assertFalse(rMax.isValid());
        assertTrue(rMax.errorMessages().getFirst().contains("exceeds maximum"));
    }

    @Test
    @DisplayName("Array size constraints")
    void testArrayConstraints() {
        JsonSchemaValidator validator = new JsonSchemaValidator()
                .defineField("items", FieldConstraint.builder()
                        .type(JsonFieldType.ARRAY)
                        .minLength(2)
                        .maxLength(4)
                        .build());

        assertTrue(validator.validate(Map.of("items", List.of("a", "b"))).isValid());
        assertTrue(validator.validate(Map.of("items", new String[]{"a", "b", "c"})).isValid());

        ValidationResult rEmpty = validator.validate(Map.of("items", List.of()));
        assertFalse(rEmpty.isValid());
        assertTrue(rEmpty.errorMessages().getFirst().contains("less than minimum"));

        ValidationResult rOverflow = validator.validate(Map.of("items", List.of(1, 2, 3, 4, 5)));
        assertFalse(rOverflow.isValid());
        assertTrue(rOverflow.errorMessages().getFirst().contains("exceeds maximum"));
    }

    @Test
    @DisplayName("Null map returns invalid result")
    void testNullDataMap() {
        JsonSchemaValidator validator = new JsonSchemaValidator();
        ValidationResult result = validator.validate(null);
        assertFalse(result.isValid());
        assertTrue(result.errorMessages().getFirst().contains("null"));
    }
}
