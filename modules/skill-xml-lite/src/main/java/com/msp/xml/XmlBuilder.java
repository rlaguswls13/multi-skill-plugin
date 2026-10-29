package com.msp.xml;

import java.util.*;

/**
 * Fluent builder for creating XML structures and generating XML strings.
 */
public final class XmlBuilder {

    private final String tag;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private String text = "";
    private final List<XmlElement> children = new ArrayList<>();

    private XmlBuilder(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Element tag cannot be null or blank");
        }
        this.tag = tag.trim();
    }

    /**
     * Starts building an XML element with the specified tag.
     *
     * @param tag the XML tag name
     * @return a new XmlBuilder instance
     */
    public static XmlBuilder element(String tag) {
        return new XmlBuilder(tag);
    }

    /**
     * Adds an attribute to this element.
     *
     * @param name attribute name
     * @param value attribute value
     * @return this builder
     */
    public XmlBuilder attr(String name, String value) {
        if (name != null && value != null) {
            this.attributes.put(name, value);
        }
        return this;
    }

    /**
     * Adds an attribute with any Object value (via toString).
     */
    public XmlBuilder attr(String name, Object value) {
        if (name != null && value != null) {
            this.attributes.put(name, String.valueOf(value));
        }
        return this;
    }

    /**
     * Adds multiple attributes from a map.
     */
    public XmlBuilder attrs(Map<String, String> attrs) {
        if (attrs != null) {
            this.attributes.putAll(attrs);
        }
        return this;
    }

    /**
     * Sets the text content of this element.
     */
    public XmlBuilder text(String text) {
        this.text = text != null ? text : "";
        return this;
    }

    /**
     * Adds a child element from another XmlBuilder.
     */
    public XmlBuilder child(XmlBuilder childBuilder) {
        if (childBuilder != null) {
            this.children.add(childBuilder.build());
        }
        return this;
    }

    /**
     * Adds a child XmlElement.
     */
    public XmlBuilder child(XmlElement childElement) {
        if (childElement != null) {
            this.children.add(childElement);
        }
        return this;
    }

    /**
     * Conveniently adds a simple child element with a tag and text.
     */
    public XmlBuilder child(String tag, String text) {
        return child(XmlBuilder.element(tag).text(text));
    }

    /**
     * Adds multiple child elements.
     */
    public XmlBuilder children(Collection<XmlElement> elements) {
        if (elements != null) {
            this.children.addAll(elements);
        }
        return this;
    }

    /**
     * Builds the XmlElement model.
     *
     * @return immutable XmlElement
     */
    public XmlElement build() {
        return new XmlElement(tag, attributes, text, children);
    }

    /**
     * Formats the element as a pretty-printed XML string.
     *
     * @return XML string
     */
    public String toXml() {
        return build().toXml(true);
    }

    /**
     * Formats the element as an XML string.
     *
     * @param pretty whether to indent and format
     * @return XML string
     */
    public String toXml(boolean pretty) {
        return build().toXml(pretty);
    }

    @Override
    public String toString() {
        return toXml();
    }
}
