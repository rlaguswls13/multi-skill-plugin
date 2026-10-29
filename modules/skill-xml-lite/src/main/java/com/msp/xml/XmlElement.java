package com.msp.xml;

import java.util.*;

/**
 * Immutable representation of an XML element.
 * Contains tag name, attributes, text content, and nested child elements.
 */
public final class XmlElement {

    private final String tag;
    private final Map<String, String> attributes;
    private final String text;
    private final List<XmlElement> children;

    public XmlElement(String tag, Map<String, String> attributes, String text, List<XmlElement> children) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Tag name must not be null or blank");
        }
        this.tag = tag.trim();
        this.attributes = attributes != null ? Collections.unmodifiableMap(new LinkedHashMap<>(attributes)) : Collections.emptyMap();
        this.text = text != null ? text : "";
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    public String tag() {
        return tag;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public String attr(String name) {
        return attributes.get(name);
    }

    public String attr(String name, String defaultValue) {
        return attributes.getOrDefault(name, defaultValue);
    }

    public String text() {
        return text.trim();
    }

    public String rawText() {
        return text;
    }

    public List<XmlElement> children() {
        return children;
    }

    public List<XmlElement> children(String childTag) {
        if (childTag == null) return List.of();
        List<XmlElement> result = new ArrayList<>();
        for (XmlElement child : children) {
            if (childTag.equals(child.tag)) {
                result.add(child);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public Optional<XmlElement> child(String childTag) {
        if (childTag == null) return Optional.empty();
        for (XmlElement child : children) {
            if (childTag.equals(child.tag)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    public Optional<String> childText(String childTag) {
        return child(childTag).map(XmlElement::text);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public String toXml() {
        return toXml(true);
    }

    public String toXml(boolean pretty) {
        StringBuilder sb = new StringBuilder();
        serialize(this, 0, pretty, sb);
        return sb.toString();
    }

    private static void serialize(XmlElement el, int indent, boolean pretty, StringBuilder sb) {
        String pad = pretty ? " ".repeat(indent * 2) : "";
        sb.append(pad).append("<").append(el.tag);

        for (Map.Entry<String, String> entry : el.attributes.entrySet()) {
            sb.append(" ").append(entry.getKey()).append("=\"")
              .append(escapeAttr(entry.getValue())).append("\"");
        }

        boolean hasText = !el.text().isEmpty();
        boolean hasChildren = !el.children.isEmpty();

        if (!hasText && !hasChildren) {
            sb.append("/>");
            if (pretty) sb.append("\n");
            return;
        }

        sb.append(">");
        if (hasChildren) {
            if (pretty) sb.append("\n");
            for (XmlElement child : el.children) {
                serialize(child, indent + 1, pretty, sb);
            }
            if (hasText) {
                sb.append(pretty ? " ".repeat((indent + 1) * 2) : "").append(escapeText(el.text));
                if (pretty) sb.append("\n");
            }
            sb.append(pad);
        } else {
            sb.append(escapeText(el.text));
        }

        sb.append("</").append(el.tag).append(">");
        if (pretty) sb.append("\n");
    }

    private static String escapeText(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    private static String escapeAttr(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XmlElement that)) return false;
        return Objects.equals(tag, that.tag) &&
               Objects.equals(attributes, that.attributes) &&
               Objects.equals(text, that.text) &&
               Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, attributes, text, children);
    }

    @Override
    public String toString() {
        return toXml(false);
    }
}
