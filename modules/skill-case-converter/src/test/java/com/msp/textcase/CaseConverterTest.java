package com.msp.textcase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CaseConverter Unit Tests")
class CaseConverterTest {

    @Nested
    @DisplayName("Acronym Handling")
    class AcronymTests {

        @Test
        @DisplayName("JSONParser should convert cleanly across all formats")
        void testJsonParser() {
            String input = "JSONParser";
            assertEquals("jsonParser", CaseConverter.toCamelCase(input));
            assertEquals("JsonParser", CaseConverter.toPascalCase(input));
            assertEquals("json_parser", CaseConverter.toSnakeCase(input));
            assertEquals("json-parser", CaseConverter.toKebabCase(input));
            assertEquals("JSON_PARSER", CaseConverter.toConstantCase(input));
            assertEquals("Json Parser", CaseConverter.toTitleCase(input));
        }

        @Test
        @DisplayName("parseJSON and getHTTPResponseCode acronym transitions")
        void testAcronymInMiddleAndEnd() {
            assertEquals("parse_json", CaseConverter.toSnakeCase("parseJSON"));
            assertEquals("parseJson", CaseConverter.toCamelCase("parseJSON"));

            assertEquals("get_http_response_code", CaseConverter.toSnakeCase("getHTTPResponseCode"));
            assertEquals("getHttpResponseCode", CaseConverter.toCamelCase("getHTTPResponseCode"));
            assertEquals("GetHttpResponseCode", CaseConverter.toPascalCase("getHTTPResponseCode"));
            assertEquals("GET_HTTP_RESPONSE_CODE", CaseConverter.toConstantCase("getHTTPResponseCode"));
        }

        @Test
        @DisplayName("Multiple consecutive acronyms and words")
        void testMultipleAcronyms() {
            assertEquals("simple_xml_parser", CaseConverter.toSnakeCase("simpleXMLParser"));
            assertEquals("simpleXmlParser", CaseConverter.toCamelCase("simpleXMLParser"));
        }
    }

    @Nested
    @DisplayName("Standard Case Conversions")
    class StandardCaseTests {

        @ParameterizedTest(name = "From snake_case: {0}")
        @CsvSource({
                "user_id, userId, UserId, user_id, user-id, USER_ID, User Id",
                "first_name, firstName, FirstName, first_name, first-name, FIRST_NAME, First Name",
                "created_at_timestamp, createdAtTimestamp, CreatedAtTimestamp, created_at_timestamp, created-at-timestamp, CREATED_AT_TIMESTAMP, Created At Timestamp"
        })
        void testFromSnakeCase(String input, String camel, String pascal, String snake, String kebab, String constant, String title) {
            assertEquals(camel, CaseConverter.toCamelCase(input));
            assertEquals(pascal, CaseConverter.toPascalCase(input));
            assertEquals(snake, CaseConverter.toSnakeCase(input));
            assertEquals(kebab, CaseConverter.toKebabCase(input));
            assertEquals(constant, CaseConverter.toConstantCase(input));
            assertEquals(title, CaseConverter.toTitleCase(input));
        }

        @ParameterizedTest(name = "From kebab-case: {0}")
        @CsvSource({
                "kebab-case-string, kebabCaseString, KebabCaseString, kebab_case_string, kebab-case-string, KEBAB_CASE_STRING",
                "custom-api-key, customApiKey, CustomApiKey, custom_api_key, custom-api-key, CUSTOM_API_KEY"
        })
        void testFromKebabCase(String input, String camel, String pascal, String snake, String kebab, String constant) {
            assertEquals(camel, CaseConverter.toCamelCase(input));
            assertEquals(pascal, CaseConverter.toPascalCase(input));
            assertEquals(snake, CaseConverter.toSnakeCase(input));
            assertEquals(kebab, CaseConverter.toKebabCase(input));
            assertEquals(constant, CaseConverter.toConstantCase(input));
        }

        @Test
        @DisplayName("Round-trip consistency")
        void testRoundTrip() {
            String originalSnake = "order_item_quantity";
            String camel = CaseConverter.toCamelCase(originalSnake);
            assertEquals("orderItemQuantity", camel);
            String roundTripSnake = CaseConverter.toSnakeCase(camel);
            assertEquals(originalSnake, roundTripSnake);

            String pascal = CaseConverter.toPascalCase(originalSnake);
            assertEquals("OrderItemQuantity", pascal);
            assertEquals(originalSnake, CaseConverter.toSnakeCase(pascal));

            String constant = CaseConverter.toConstantCase(originalSnake);
            assertEquals("ORDER_ITEM_QUANTITY", constant);
            assertEquals(originalSnake, CaseConverter.toSnakeCase(constant));
        }
    }

    @Nested
    @DisplayName("Numbers and Alphanumeric Identifiers")
    class NumberTests {

        @Test
        @DisplayName("Numbers adjacent to letters")
        void testNumbersInIdentifiers() {
            assertEquals("order123_status", CaseConverter.toSnakeCase("order123Status"));
            assertEquals("order123Status", CaseConverter.toCamelCase("order123Status"));
            assertEquals("Order123Status", CaseConverter.toPascalCase("order123Status"));

            assertEquals("utf8_string", CaseConverter.toSnakeCase("utf8String"));
            assertEquals("utf8String", CaseConverter.toCamelCase("utf8String"));
            assertEquals("Utf8String", CaseConverter.toPascalCase("utf8String"));

            assertEquals("item1", CaseConverter.toCamelCase("item1"));
            assertEquals("Item1", CaseConverter.toPascalCase("item1"));
            assertEquals("ITEM1", CaseConverter.toConstantCase("item1"));
        }
    }

    @Nested
    @DisplayName("Edge Cases & Boundaries")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null and empty handling")
        void testNullAndEmpty() {
            assertNull(CaseConverter.toCamelCase(null));
            assertNull(CaseConverter.toSnakeCase(null));
            assertNull(CaseConverter.toPascalCase(null));
            assertNull(CaseConverter.toKebabCase(null));
            assertNull(CaseConverter.toConstantCase(null));
            assertNull(CaseConverter.toTitleCase(null));

            assertEquals("", CaseConverter.toCamelCase(""));
            assertEquals("", CaseConverter.toSnakeCase(""));
            assertEquals("", CaseConverter.toPascalCase(""));
            assertEquals("", CaseConverter.toKebabCase(""));
            assertEquals("", CaseConverter.toConstantCase(""));
            assertEquals("", CaseConverter.toTitleCase(""));
        }

        @Test
        @DisplayName("Blank strings and strings with only delimiters")
        void testDelimitersOnly() {
            assertEquals("", CaseConverter.toCamelCase("   "));
            assertEquals("", CaseConverter.toSnakeCase("___"));
            assertEquals("", CaseConverter.toPascalCase("---"));
        }

        @Test
        @DisplayName("Leading, trailing, and multiple consecutive delimiters")
        void testRepeatedDelimiters() {
            assertEquals("helloWorld", CaseConverter.toCamelCase("__hello___world__"));
            assertEquals("hello_world", CaseConverter.toSnakeCase("---hello---world---"));
            assertEquals("HelloWorld", CaseConverter.toPascalCase("  hello   world  "));
        }

        @Test
        @DisplayName("Single character inputs")
        void testSingleChar() {
            assertEquals("a", CaseConverter.toCamelCase("a"));
            assertEquals("A", CaseConverter.toPascalCase("a"));
            assertEquals("a", CaseConverter.toSnakeCase("a"));
            assertEquals("A", CaseConverter.toConstantCase("a"));

            assertEquals("a", CaseConverter.toCamelCase("A"));
            assertEquals("A", CaseConverter.toPascalCase("A"));
            assertEquals("a", CaseConverter.toSnakeCase("A"));
            assertEquals("A", CaseConverter.toConstantCase("A"));
        }

        @Test
        @DisplayName("Word split inspection")
        void testSplitWords() {
            List<String> words = CaseConverter.splitWords("JSONParserService2Provider");
            assertEquals(List.of("JSON", "Parser", "Service2", "Provider"), words);
        }
    }
}
