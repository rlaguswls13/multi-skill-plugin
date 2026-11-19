package com.msp.sfv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuredFieldsTest {

    @Test
    void testParseAndSerializeBareItemsWithParams() {
        // Integer item with parameters
        SfItem intItem = StructuredFields.parseItem("  42;foo=bar;baz=?1;num=12.5  ");
        assertEquals(BareItem.ofInteger(42), intItem.value());
        assertEquals(3, intItem.params().size());
        assertEquals(BareItem.ofToken("bar"), intItem.params().get("foo"));
        assertEquals(BareItem.ofBoolean(true), intItem.params().get("baz"));
        assertEquals(BareItem.ofDecimal(new BigDecimal("12.5")), intItem.params().get("num"));
        // Canonical serialization omits ";baz=?1" value and outputs ";baz"
        assertEquals("42;foo=bar;baz;num=12.5", StructuredFields.serialize(intItem));
    }

    @Test
    void testStringEscapingAndByteSequence() {
        SfItem strItem = StructuredFields.parseItem("\"hello \\\"world\\\" \\\\ path\"");
        assertEquals(BareItem.ofString("hello \"world\" \\ path"), strItem.value());
        assertEquals("\"hello \\\"world\\\" \\\\ path\"", StructuredFields.serialize(strItem));

        byte[] raw = "Pretend this is binary content.".getBytes(StandardCharsets.UTF_8);
        SfItem byteItem = SfItem.of(BareItem.ofBytes(raw));
        String serialized = StructuredFields.serialize(byteItem);
        assertEquals(":UHJldGVuZCB0aGlzIGlzIGJpbmFyeSBjb250ZW50Lg==:", serialized);
        SfItem roundTrip = StructuredFields.parseItem(serialized);
        assertArrayEquals(raw, ((BareItem.SfByteSequence) roundTrip.value()).value());
    }

    @Test
    void testDecimalNormalizationAndBounds() {
        SfItem decItem = StructuredFields.parseItem("-123456789012.500");
        assertEquals("-123456789012.5", StructuredFields.serialize(decItem));

        SfItem zeroDec = StructuredFields.parseItem("-0.00");
        assertEquals("0.0", StructuredFields.serialize(zeroDec));

        // 16-digit integer must fail
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseItem("1000000000000000"));
        // 13-digit integer part in decimal must fail
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseItem("1000000000000.1"));
        // 4 fractional digits in decimal must fail
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseItem("12.3456"));
    }

    @Test
    void testParseAndSerializeListWithInnerLists() {
        String header = "sugar, tea;q=0.8, (\"milk\" \"honey\";organic);source=local, :AQID:";
        SfList list = StructuredFields.parseList(header);
        assertEquals(4, list.members().size());

        assertInstanceOf(SfItem.class, list.members().get(0));
        assertInstanceOf(SfItem.class, list.members().get(1));
        assertInstanceOf(SfInnerList.class, list.members().get(2));
        assertInstanceOf(SfItem.class, list.members().get(3));

        SfInnerList inner = (SfInnerList) list.members().get(2);
        assertEquals(2, inner.items().size());
        assertEquals(BareItem.ofString("milk"), inner.items().get(0).value());
        assertEquals(BareItem.ofBoolean(true), inner.items().get(1).params().get("organic"));
        assertEquals(BareItem.ofToken("local"), inner.params().get("source"));

        assertEquals(header, StructuredFields.serialize(list));
    }

    @Test
    void testParseAndSerializeDictionary() {
        // RFC 8941 Priority / Cache-Control style dictionary
        String header = "u=3, i, en=\"Applepie\", list=(1 2 3);v=1, f=?0;reason=disabled";
        SfDictionary dict = StructuredFields.parseDictionary(header);

        assertEquals(5, dict.entries().size());
        assertEquals(SfItem.of(BareItem.ofInteger(3)), dict.entries().get("u"));
        assertEquals(SfItem.of(BareItem.ofBoolean(true)), dict.entries().get("i"));
        assertEquals(SfItem.of(BareItem.ofString("Applepie")), dict.entries().get("en"));
        assertInstanceOf(SfInnerList.class, dict.entries().get("list"));

        SfItem fItem = (SfItem) dict.entries().get("f");
        assertEquals(BareItem.ofBoolean(false), fItem.value());
        assertEquals(BareItem.ofToken("disabled"), fItem.params().get("reason"));

        assertEquals(header, StructuredFields.serialize(dict));
    }

    @Test
    void testDuplicateKeysOverrideInOrder() {
        SfDictionary dict = StructuredFields.parseDictionary("a=1, b=2, a=3");
        assertEquals(2, dict.entries().size());
        assertEquals("a=3, b=2", StructuredFields.serialize(dict));

        SfItem item = StructuredFields.parseItem("token;k=1;k=2");
        assertEquals("token;k=2", StructuredFields.serialize(item));
    }

    @Test
    void testInvalidSyntaxRejection() {
        // Invalid escaped character in string
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseItem("\"hello\\nworld\""));
        // Uppercase char in key
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseDictionary("BadKey=1"));
        // Trailing comma in list
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseList("a, b, "));
        // Trailing comma in dictionary
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseDictionary("a=1, "));
        // Unclosed inner list
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseList("(1 2"));
        // Invalid boolean
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseItem("?2"));
        // Invalid base64
        assertThrows(StructuredFieldException.class, () -> StructuredFields.parseItem(":not_base64!:"));
    }

    @Test
    void testProgrammaticConstruction() {
        Map<String, SfMember> map = new LinkedHashMap<>();
        map.put("max-age", SfItem.of(BareItem.ofInteger(3600)));
        map.put("must-revalidate", SfItem.of(BareItem.ofBoolean(true)));
        map.put("stale-while-revalidate", SfItem.of(BareItem.ofInteger(60), Map.of("fast", BareItem.ofBoolean(true))));
        SfDictionary dict = new SfDictionary(map);

        assertEquals("max-age=3600, must-revalidate, stale-while-revalidate=60;fast",
                StructuredFields.serialize(dict));
    }
}
