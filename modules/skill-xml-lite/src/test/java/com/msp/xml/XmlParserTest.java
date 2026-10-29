package com.msp.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XmlParser & XmlBuilder Tests")
class XmlParserTest {

    @Test
    @DisplayName("Should parse simple XML with attributes and text")
    void testSimpleXml() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <user id="u123" role="admin">
                Alice &amp; Bob
            </user>
            """;

        XmlElement root = XmlParser.parse(xml);
        assertEquals("user", root.tag());
        assertEquals("u123", root.attr("id"));
        assertEquals("admin", root.attr("role"));
        assertEquals("Alice & Bob", root.text());
        assertTrue(root.children().isEmpty());
    }

    @Test
    @DisplayName("Should parse nested elements and self-closing tags")
    void testNestedAndSelfClosing() {
        String xml = """
            <catalog name="electronics">
                <!-- Section 1 -->
                <item id="i1" name="Keyboard" price="50.0"/>
                <item id="i2" name="Mouse" price="25.0">
                    <spec dpi="1600"/>
                </item>
            </catalog>
            """;

        XmlElement root = XmlParser.parse(xml);
        assertEquals("catalog", root.tag());
        assertEquals("electronics", root.attr("name"));

        List<XmlElement> items = root.children("item");
        assertEquals(2, items.size());

        XmlElement item1 = items.get(0);
        assertEquals("i1", item1.attr("id"));
        assertEquals("Keyboard", item1.attr("name"));
        assertEquals("50.0", item1.attr("price"));
        assertTrue(item1.children().isEmpty());

        XmlElement item2 = items.get(1);
        assertEquals("i2", item2.attr("id"));
        assertEquals("Mouse", item2.attr("name"));
        assertTrue(item2.child("spec").isPresent());
        assertEquals("1600", item2.child("spec").get().attr("dpi"));
    }

    @Test
    @DisplayName("Should handle CDATA and entity escaping correctly")
    void testCDataAndEntities() {
        String xml = """
            <message id="m1">
                <description><![CDATA[Some <b>HTML</b> & special text]]></description>
                <quote>He said: &quot;&apos;Hello &amp; welcome!&apos;&quot;</quote>
            </message>
            """;

        XmlElement root = XmlParser.parse(xml);
        assertEquals("Some <b>HTML</b> & special text", root.childText("description").orElse(""));
        assertEquals("He said: \"'Hello & welcome!'\"", root.childText("quote").orElse(""));
    }

    @Test
    @DisplayName("Should build XML using XmlBuilder fluent API")
    void testXmlBuilder() {
        String xml = XmlBuilder.element("root")
                .attr("id", "1")
                .child(XmlBuilder.element("item")
                        .attr("type", "primary")
                        .text("Item A"))
                .child(XmlBuilder.element("item")
                        .attr("type", "secondary")
                        .text("Item B"))
                .toXml(false);

        assertEquals("<root id=\"1\"><item type=\"primary\">Item A</item><item type=\"secondary\">Item B</item></root>", xml);
    }

    @Test
    @DisplayName("Should round-trip serialize and parse")
    void testRoundTrip() {
        XmlElement original = XmlBuilder.element("order")
                .attr("orderId", "ORD-999")
                .child(XmlBuilder.element("customer")
                        .attr("vip", "true")
                        .text("John Doe"))
                .child(XmlBuilder.element("product")
                        .attr("sku", "SKU-001")
                        .attr("qty", "2"))
                .build();

        String xml = original.toXml();
        XmlElement parsed = XmlParser.parse(xml);

        assertEquals(original.tag(), parsed.tag());
        assertEquals("ORD-999", parsed.attr("orderId"));
        assertEquals("John Doe", parsed.childText("customer").orElse(""));
        assertEquals("true", parsed.child("customer").get().attr("vip"));
        assertEquals("SKU-001", parsed.child("product").get().attr("sku"));
        assertEquals("2", parsed.child("product").get().attr("qty"));
    }

    @Test
    @DisplayName("Should fail on mismatched tags")
    void testMismatchedTags() {
        String invalid = "<root><child>value</other></root>";
        assertThrows(XmlException.class, () -> XmlParser.parse(invalid));
    }

    @Test
    @DisplayName("Should fail on unclosed tag")
    void testUnclosedTag() {
        String invalid = "<root><child>value</root>";
        assertThrows(XmlException.class, () -> XmlParser.parse(invalid));
    }

    @Test
    @DisplayName("Should fail on empty or null input")
    void testEmptyInput() {
        assertThrows(XmlException.class, () -> XmlParser.parse(""));
        assertThrows(XmlException.class, () -> XmlParser.parse(null));
    }
}
