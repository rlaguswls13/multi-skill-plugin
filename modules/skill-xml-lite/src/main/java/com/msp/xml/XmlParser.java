package com.msp.xml;

import java.util.*;

/**
 * Pure Java 21 lightweight XML parser with zero external dependencies.
 * Efficiently parses XML into an immutable {@link XmlElement} hierarchy.
 */
public final class XmlParser {

    private final String src;
    private final int len;
    private int pos = 0;

    private static final class NodeState {
        final String tag;
        final Map<String, String> attrs;
        final StringBuilder text = new StringBuilder();
        final List<XmlElement> children = new ArrayList<>();

        NodeState(String tag, Map<String, String> attrs) {
            this.tag = tag;
            this.attrs = attrs;
        }

        XmlElement toElement() {
            return new XmlElement(tag, attrs, text.toString(), children);
        }
    }

    private XmlParser(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new XmlException("XML input is null or empty");
        }
        this.src = xml;
        this.len = xml.length();
    }

    /**
     * Parses an XML string into a root {@link XmlElement}.
     *
     * @param xmlString the XML source string
     * @return parsed root XmlElement
     * @throws XmlException if the XML is malformed
     */
    public static XmlElement parse(String xmlString) {
        return new XmlParser(xmlString).parseInternal();
    }

    private XmlElement parseInternal() {
        Deque<NodeState> stack = new ArrayDeque<>();
        XmlElement root = null;

        while (pos < len) {
            int nextLt = src.indexOf('<', pos);
            if (nextLt == -1) {
                // Remaining trailing text/whitespace
                break;
            }

            if (nextLt > pos && !stack.isEmpty()) {
                String text = src.substring(pos, nextLt);
                stack.peek().text.append(unescape(text));
            }

            pos = nextLt;

            if (src.startsWith("<!--", pos)) {
                // Comment
                int end = src.indexOf("-->", pos + 4);
                if (end == -1) throw new XmlException("Unclosed comment at position " + pos);
                pos = end + 3;
            } else if (src.startsWith("<?", pos)) {
                // Processing instruction / XML declaration
                int end = src.indexOf("?>", pos + 2);
                if (end == -1) throw new XmlException("Unclosed processing instruction at position " + pos);
                pos = end + 2;
            } else if (src.startsWith("<![CDATA[", pos)) {
                // CDATA section
                if (stack.isEmpty()) throw new XmlException("CDATA found outside of root element at position " + pos);
                int end = src.indexOf("]]>", pos + 9);
                if (end == -1) throw new XmlException("Unclosed CDATA at position " + pos);
                stack.peek().text.append(src, pos + 9, end);
                pos = end + 3;
            } else if (src.startsWith("<!DOCTYPE", pos)) {
                // DOCTYPE
                pos = skipDocType(pos);
            } else if (src.startsWith("</", pos)) {
                // Closing tag
                int closeEnd = src.indexOf('>', pos + 2);
                if (closeEnd == -1) throw new XmlException("Unclosed end tag at position " + pos);
                String closeTag = src.substring(pos + 2, closeEnd).trim();
                pos = closeEnd + 1;

                if (stack.isEmpty()) {
                    throw new XmlException("Unexpected closing tag </" + closeTag + "> with empty stack");
                }
                NodeState top = stack.pop();
                if (!top.tag.equals(closeTag)) {
                    throw new XmlException("Tag mismatch: expected </" + top.tag + ">, found </" + closeTag + ">");
                }

                XmlElement elem = top.toElement();
                if (stack.isEmpty()) {
                    root = elem;
                } else {
                    stack.peek().children.add(elem);
                }
            } else {
                // Opening or self-closing tag
                int tagEnd = findTagEnd(pos + 1);
                if (tagEnd == -1) throw new XmlException("Unclosed tag starting at position " + pos);

                boolean selfClosing = src.charAt(tagEnd - 1) == '/';
                String tagContent = selfClosing
                        ? src.substring(pos + 1, tagEnd - 1).trim()
                        : src.substring(pos + 1, tagEnd).trim();

                pos = tagEnd + 1;

                ParsedTag parsed = parseTagContent(tagContent);

                if (selfClosing) {
                    XmlElement elem = new XmlElement(parsed.tag, parsed.attributes, "", List.of());
                    if (stack.isEmpty()) {
                        root = elem;
                    } else {
                        stack.peek().children.add(elem);
                    }
                } else {
                    stack.push(new NodeState(parsed.tag, parsed.attributes));
                }
            }
        }

        if (!stack.isEmpty()) {
            throw new XmlException("Unclosed tag: <" + stack.peek().tag + ">");
        }
        if (root == null) {
            throw new XmlException("No root XML element found");
        }
        return root;
    }

    private int findTagEnd(int start) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = start; i < len; i++) {
            char c = src.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '>' && !inSingle && !inDouble) return i;
        }
        return -1;
    }

    private int skipDocType(int start) {
        boolean inBracket = false;
        for (int i = start + 9; i < len; i++) {
            char c = src.charAt(i);
            if (c == '[') inBracket = true;
            else if (c == ']') inBracket = false;
            else if (c == '>' && !inBracket) return i + 1;
        }
        throw new XmlException("Unclosed DOCTYPE declaration");
    }

    private record ParsedTag(String tag, Map<String, String> attributes) {}

    private ParsedTag parseTagContent(String content) {
        int i = 0;
        int n = content.length();
        while (i < n && !Character.isWhitespace(content.charAt(i))) {
            i++;
        }
        String tag = content.substring(0, i);
        Map<String, String> attrs = new LinkedHashMap<>();

        while (i < n) {
            while (i < n && Character.isWhitespace(content.charAt(i))) i++;
            if (i >= n) break;

            int keyStart = i;
            while (i < n && content.charAt(i) != '=' && !Character.isWhitespace(content.charAt(i))) i++;
            String key = content.substring(keyStart, i).trim();

            while (i < n && Character.isWhitespace(content.charAt(i))) i++;
            if (i < n && content.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(content.charAt(i))) i++;
                if (i < n) {
                    char quote = content.charAt(i);
                    if (quote == '"' || quote == '\'') {
                        i++;
                        int valStart = i;
                        while (i < n && content.charAt(i) != quote) i++;
                        String val = content.substring(valStart, i);
                        attrs.put(key, unescape(val));
                        if (i < n) i++; // skip quote
                    } else {
                        int valStart = i;
                        while (i < n && !Character.isWhitespace(content.charAt(i))) i++;
                        attrs.put(key, unescape(content.substring(valStart, i)));
                    }
                }
            } else {
                attrs.put(key, "");
            }
        }
        return new ParsedTag(tag, attrs);
    }

    public static String unescape(String s) {
        if (s == null || !s.contains("&")) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i + 1);
                if (semi != -1 && semi - i <= 10) {
                    String entity = s.substring(i + 1, semi);
                    switch (entity) {
                        case "amp" -> { sb.append('&'); i = semi; }
                        case "lt" -> { sb.append('<'); i = semi; }
                        case "gt" -> { sb.append('>'); i = semi; }
                        case "quot" -> { sb.append('"'); i = semi; }
                        case "apos" -> { sb.append('\''); i = semi; }
                        default -> {
                            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                                try {
                                    int codePoint = Integer.parseInt(entity.substring(2), 16);
                                    sb.append(Character.toChars(codePoint));
                                    i = semi;
                                } catch (Exception e) {
                                    sb.append('&');
                                }
                            } else if (entity.startsWith("#")) {
                                try {
                                    int codePoint = Integer.parseInt(entity.substring(1), 10);
                                    sb.append(Character.toChars(codePoint));
                                    i = semi;
                                } catch (Exception e) {
                                    sb.append('&');
                                }
                            } else {
                                sb.append('&');
                            }
                        }
                    }
                } else {
                    sb.append('&');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
