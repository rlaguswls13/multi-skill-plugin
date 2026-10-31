package com.msp.jsonmasker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMaskerTest {

    @Test
    @DisplayName("Exact root path masking: password field masked to ***")
    void testExactRootPath() {
        JsonMasker masker = JsonMasker.create()
                .withMask("password", MaskingStrategy.FULL);

        String json = "{\"username\":\"alice\",\"password\":\"secret123\"}";
        String masked = masker.mask(json);

        assertTrue(masked.contains("\"username\":\"alice\""));
        assertTrue(masked.contains("\"password\":\"***\""));
    }

    @Test
    @DisplayName("Nested object path: user.password masked while admin.password untouched")
    void testNestedObjectPath() {
        JsonMasker masker = JsonMasker.create()
                .withMask("user.password", MaskingStrategy.FULL);

        String json = "{\"user\":{\"password\":\"userPass\",\"role\":\"guest\"},\"admin\":{\"password\":\"adminPass\"}}";
        String masked = masker.mask(json);

        assertTrue(masked.contains("\"user\":{\"password\":\"***\",\"role\":\"guest\"}"));
        assertTrue(masked.contains("\"admin\":{\"password\":\"adminPass\"}"));
    }

    @Test
    @DisplayName("Wildcard object path (*.ssn): masks ssn at any 1st-level object")
    void testWildcardObjectPath() {
        JsonMasker masker = JsonMasker.create()
                .withMask("*.ssn", MaskingStrategy.FULL);

        String json = "{\"customer\":{\"ssn\":\"123-45-6789\",\"name\":\"Bob\"},\"employee\":{\"ssn\":\"987-65-4321\"}}";
        String masked = masker.mask(json);

        assertTrue(masked.contains("\"customer\":{\"ssn\":\"***\",\"name\":\"Bob\"}"));
        assertTrue(masked.contains("\"employee\":{\"ssn\":\"***\"}"));
    }

    @Test
    @DisplayName("Array wildcard path (users[*].email): masks emails for all array items")
    void testArrayWildcardPath() {
        JsonMasker masker = JsonMasker.create()
                .withMask("users[*].email", MaskingStrategy.PARTIAL_EMAIL);

        String json = "{\"users\":[{\"id\":1,\"email\":\"alice@example.com\"},{\"id\":2,\"email\":\"bob.smith@corp.org\"}]}";
        String masked = masker.mask(json);

        assertTrue(masked.contains("\"email\":\"a***@example.com\""));
        assertTrue(masked.contains("\"email\":\"b***@corp.org\""));
        assertTrue(masked.contains("\"id\":1"));
        assertTrue(masked.contains("\"id\":2"));
    }

    @Test
    @DisplayName("Credit card partial masking (PARTIAL_CARD)")
    void testPartialCardMasking() {
        JsonMasker masker = JsonMasker.create()
                .withMask("cardHyphen", MaskingStrategy.PARTIAL_CARD)
                .withMask("cardContinuous", MaskingStrategy.PARTIAL_CARD);

        String json = "{\"cardHyphen\":\"4111-2222-3333-1111\",\"cardContinuous\":\"4111222233331111\"}";
        String masked = masker.mask(json);

        assertTrue(masked.contains("\"cardHyphen\":\"4111-****-****-1111\""));
        assertTrue(masked.contains("\"cardContinuous\":\"4111-****-****-1111\""));
    }

    @Test
    @DisplayName("Partial email masking (PARTIAL_EMAIL)")
    void testPartialEmailMasking() {
        JsonMasker masker = JsonMasker.create()
                .withMask("contactEmail", MaskingStrategy.PARTIAL_EMAIL);

        String json = "{\"contactEmail\":\"john.doe@domain.com\"}";
        String masked = masker.mask(json);

        assertTrue(masked.contains("\"contactEmail\":\"j***@domain.com\""));
    }

    @Test
    @DisplayName("Custom masking strategy with fixed replacement")
    void testCustomMaskingStrategy() {
        JsonMasker masker = JsonMasker.create()
                .withMask("secretKey", MaskingStrategy.custom("[REDACTED_BY_POLICY]"));

        String json = "{\"secretKey\":\"xyz-super-secret\"}";
        String masked = masker.mask(json);

        assertTrue(masked.contains("\"secretKey\":\"[REDACTED_BY_POLICY]\""));
    }

    @Test
    @DisplayName("Invalid JSON pass-through: malformed input safely returned as-is")
    void testInvalidJsonPassThrough() {
        JsonMasker masker = JsonMasker.create()
                .withMask("password", MaskingStrategy.FULL);

        String malformed1 = "{not valid json at all}";
        String malformed2 = "{\"password\": unquoted}";
        String malformed3 = "random text string";

        assertEquals(malformed1, masker.mask(malformed1));
        assertEquals(malformed2, masker.mask(malformed2));
        assertEquals(malformed3, masker.mask(malformed3));
        assertNull(masker.mask(null));
        assertEquals("", masker.mask(""));
    }

    @Test
    @DisplayName("Preserving data types: booleans, numbers, nulls, and non-target fields")
    void testPreserveDataTypes() {
        JsonMasker masker = JsonMasker.create()
                .withMask("token", MaskingStrategy.FULL);

        String json = "{\"token\":\"abc\",\"count\":42,\"pi\":3.14,\"active\":true,\"empty\":null}";
        String masked = masker.mask(json);

        assertTrue(masked.contains("\"token\":\"***\""));
        assertTrue(masked.contains("\"count\":42"));
        assertTrue(masked.contains("\"pi\":3.14"));
        assertTrue(masked.contains("\"active\":true"));
        assertTrue(masked.contains("\"empty\":null"));
    }
}
