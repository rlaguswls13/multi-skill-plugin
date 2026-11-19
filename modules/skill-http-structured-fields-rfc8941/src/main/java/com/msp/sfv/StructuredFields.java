package com.msp.sfv;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 8941 / RFC 9651 HTTP Structured Field Values Strict Parser and Canonical Serializer.
 */
public final class StructuredFields {

    private StructuredFields() {
    }

    /**
     * Parses an RFC 8941 Item from a header field value string.
     */
    public static SfItem parseItem(String input) {
        Objects.requireNonNull(input, "Header input must not be null");
        Parser p = new Parser(input);
        p.skipSP();
        SfItem item = p.parseItem();
        p.skipSP();
        p.expectEnd();
        return item;
    }

    /**
     * Parses an RFC 8941 List from a header field value string.
     */
    public static SfList parseList(String input) {
        Objects.requireNonNull(input, "Header input must not be null");
        Parser p = new Parser(input);
        p.skipSP();
        List<SfMember> members = new ArrayList<>();
        while (!p.isAtEnd()) {
            members.add(p.parseItemOrInnerList());
            p.skipOWS();
            if (p.isAtEnd()) {
                break;
            }
            p.expectChar(',');
            p.skipOWS();
            if (p.isAtEnd()) {
                throw p.error("Trailing comma in List");
            }
        }
        return new SfList(members);
    }

    /**
     * Parses an RFC 8941 Dictionary from a header field value string.
     */
    public static SfDictionary parseDictionary(String input) {
        Objects.requireNonNull(input, "Header input must not be null");
        Parser p = new Parser(input);
        p.skipSP();
        Map<String, SfMember> dict = new LinkedHashMap<>();
        while (!p.isAtEnd()) {
            String key = p.parseKey();
            SfMember member;
            if (!p.isAtEnd() && p.peek() == '=') {
                p.advance();
                member = p.parseItemOrInnerList();
            } else {
                Map<String, BareItem> params = p.parseParameters();
                member = new SfItem(BareItem.SfBoolean.TRUE, params);
            }
            dict.put(key, member);
            p.skipOWS();
            if (p.isAtEnd()) {
                break;
            }
            p.expectChar(',');
            p.skipOWS();
            if (p.isAtEnd()) {
                throw p.error("Trailing comma in Dictionary");
            }
        }
        return new SfDictionary(dict);
    }

    /**
     * Serializes an {@link SfItem} to its canonical RFC 8941 representation.
     */
    public static String serialize(SfItem item) {
        Objects.requireNonNull(item, "SfItem must not be null");
        StringBuilder sb = new StringBuilder();
        appendItem(sb, item);
        return sb.toString();
    }

    /**
     * Serializes an {@link SfInnerList} to its canonical RFC 8941 representation.
     */
    public static String serialize(SfInnerList innerList) {
        Objects.requireNonNull(innerList, "SfInnerList must not be null");
        StringBuilder sb = new StringBuilder();
        appendInnerList(sb, innerList);
        return sb.toString();
    }

    /**
     * Serializes an {@link SfList} to its canonical RFC 8941 representation.
     */
    public static String serialize(SfList list) {
        Objects.requireNonNull(list, "SfList must not be null");
        StringBuilder sb = new StringBuilder();
        List<SfMember> members = list.members();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            appendMember(sb, members.get(i));
        }
        return sb.toString();
    }

    /**
     * Serializes an {@link SfDictionary} to its canonical RFC 8941 representation.
     */
    public static String serialize(SfDictionary dictionary) {
        Objects.requireNonNull(dictionary, "SfDictionary must not be null");
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, SfMember>> it = dictionary.entries().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SfMember> entry = it.next();
            sb.append(entry.getKey());
            SfMember member = entry.getValue();
            if (member instanceof SfItem item && item.value().equals(BareItem.SfBoolean.TRUE)) {
                appendParameters(sb, item.params());
            } else {
                sb.append('=');
                appendMember(sb, member);
            }
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Serializes a standalone {@link BareItem} to its canonical RFC 8941 string.
     */
    public static String serializeBareItem(BareItem bareItem) {
        Objects.requireNonNull(bareItem, "BareItem must not be null");
        StringBuilder sb = new StringBuilder();
        appendBareItem(sb, bareItem);
        return sb.toString();
    }

    private static void appendMember(StringBuilder sb, SfMember member) {
        switch (member) {
            case SfItem item -> appendItem(sb, item);
            case SfInnerList innerList -> appendInnerList(sb, innerList);
        }
    }

    private static void appendItem(StringBuilder sb, SfItem item) {
        appendBareItem(sb, item.value());
        appendParameters(sb, item.params());
    }

    private static void appendInnerList(StringBuilder sb, SfInnerList innerList) {
        sb.append('(');
        List<SfItem> items = innerList.items();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            appendItem(sb, items.get(i));
        }
        sb.append(')');
        appendParameters(sb, innerList.params());
    }

    private static void appendParameters(StringBuilder sb, Map<String, BareItem> params) {
        for (Map.Entry<String, BareItem> entry : params.entrySet()) {
            sb.append(';').append(entry.getKey());
            BareItem val = entry.getValue();
            if (!val.equals(BareItem.SfBoolean.TRUE)) {
                sb.append('=');
                appendBareItem(sb, val);
            }
        }
    }

    private static void appendBareItem(StringBuilder sb, BareItem item) {
        switch (item) {
            case BareItem.SfInteger i -> sb.append(i.value());
            case BareItem.SfDecimal d -> sb.append(d.value().toPlainString());
            case BareItem.SfString s -> {
                sb.append('"');
                String str = s.value();
                for (int idx = 0; idx < str.length(); idx++) {
                    char c = str.charAt(idx);
                    if (c == '\\' || c == '"') {
                        sb.append('\\');
                    }
                    sb.append(c);
                }
                sb.append('"');
            }
            case BareItem.SfToken t -> sb.append(t.value());
            case BareItem.SfByteSequence b -> sb.append(':').append(b.toBase64()).append(':');
            case BareItem.SfBoolean bool -> sb.append(bool.value() ? "?1" : "?0");
        }
    }

    private static final class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input;
            this.pos = 0;
        }

        boolean isAtEnd() {
            return pos >= input.length();
        }

        char peek() {
            return input.charAt(pos);
        }

        char advance() {
            return input.charAt(pos++);
        }

        void skipSP() {
            while (!isAtEnd() && peek() == ' ') {
                pos++;
            }
        }

        void skipOWS() {
            while (!isAtEnd() && (peek() == ' ' || peek() == '\t')) {
                pos++;
            }
        }

        void expectChar(char expected) {
            if (isAtEnd() || peek() != expected) {
                throw error("Expected '" + expected + "'");
            }
            pos++;
        }

        void expectEnd() {
            if (!isAtEnd()) {
                throw error("Unexpected trailing characters: '" + input.substring(pos) + "'");
            }
        }

        StructuredFieldException error(String message) {
            return new StructuredFieldException(message, pos);
        }

        SfMember parseItemOrInnerList() {
            if (!isAtEnd() && peek() == '(') {
                return parseInnerList();
            }
            return parseItem();
        }

        SfInnerList parseInnerList() {
            expectChar('(');
            List<SfItem> items = new ArrayList<>();
            while (!isAtEnd()) {
                skipSP();
                if (!isAtEnd() && peek() == ')') {
                    pos++;
                    Map<String, BareItem> params = parseParameters();
                    return new SfInnerList(items, params);
                }
                SfItem item = parseItem();
                items.add(item);
                if (isAtEnd() || (peek() != ' ' && peek() != ')')) {
                    throw error("Expected SP or ')' in Inner List");
                }
            }
            throw error("Unterminated Inner List");
        }

        SfItem parseItem() {
            BareItem bareItem = parseBareItem();
            Map<String, BareItem> params = parseParameters();
            return new SfItem(bareItem, params);
        }

        Map<String, BareItem> parseParameters() {
            Map<String, BareItem> params = new LinkedHashMap<>();
            while (!isAtEnd() && peek() == ';') {
                pos++; // consume ';'
                skipSP();
                String key = parseKey();
                BareItem value = BareItem.SfBoolean.TRUE;
                if (!isAtEnd() && peek() == '=') {
                    pos++; // consume '='
                    value = parseBareItem();
                }
                params.put(key, value);
            }
            return params;
        }

        String parseKey() {
            if (isAtEnd()) {
                throw error("Expected key but reached end of input");
            }
            char first = peek();
            if (!SfMember.isLcAlpha(first) && first != '*') {
                throw error("Invalid key start character '" + first + "'");
            }
            int start = pos++;
            while (!isAtEnd() && SfMember.isKeyChar(peek())) {
                pos++;
            }
            return input.substring(start, pos);
        }

        BareItem parseBareItem() {
            if (isAtEnd()) {
                throw error("Expected bare item but reached end of input");
            }
            char c = peek();
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseIntegerOrDecimal();
            } else if (c == '"') {
                return parseString();
            } else if (BareItem.SfToken.isAlpha(c) || c == '*') {
                return parseToken();
            } else if (c == ':') {
                return parseByteSequence();
            } else if (c == '?') {
                return parseBoolean();
            }
            throw error("Unrecognized bare item start character '" + c + "'");
        }

        BareItem parseIntegerOrDecimal() {
            int start = pos;
            boolean negative = false;
            if (peek() == '-') {
                negative = true;
                pos++;
            }
            if (isAtEnd() || peek() < '0' || peek() > '9') {
                throw new StructuredFieldException("Expected digit after '-'", start);
            }
            int digitsStart = pos;
            int dotIndex = -1;
            while (!isAtEnd()) {
                char c = peek();
                if (c >= '0' && c <= '9') {
                    pos++;
                    int currentDigits = (dotIndex == -1) ? (pos - digitsStart) : (dotIndex - digitsStart);
                    if (dotIndex == -1 && currentDigits > 15) {
                        throw new StructuredFieldException("Integer exceeds 15 digits", start);
                    }
                    if (dotIndex != -1 && (pos - dotIndex - 1) > 3) {
                        throw new StructuredFieldException("Decimal fraction exceeds 3 digits", start);
                    }
                } else if (c == '.' && dotIndex == -1) {
                    int intDigits = pos - digitsStart;
                    if (intDigits > 12) {
                        throw new StructuredFieldException("Decimal integer part exceeds 12 digits", start);
                    }
                    dotIndex = pos;
                    pos++;
                } else {
                    break;
                }
            }
            String raw = input.substring(start, pos);
            if (dotIndex == -1) {
                long val = Long.parseLong(raw);
                return new BareItem.SfInteger(val);
            } else {
                if (pos == dotIndex + 1) {
                    throw new StructuredFieldException("Decimal must have at least one fractional digit", start);
                }
                BigDecimal bd = new BigDecimal(raw);
                if (negative && bd.compareTo(BigDecimal.ZERO) == 0) {
                    bd = BigDecimal.ZERO.setScale(1);
                }
                return new BareItem.SfDecimal(bd);
            }
        }

        BareItem.SfString parseString() {
            expectChar('"');
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd()) {
                char c = advance();
                if (c == '\\') {
                    if (isAtEnd()) {
                        throw error("Unterminated escape sequence in String");
                    }
                    char escaped = advance();
                    if (escaped != '"' && escaped != '\\') {
                        throw new StructuredFieldException(
                                "Invalid escaped character '\\" + escaped + "' in RFC 8941 String", pos - 1);
                    }
                    sb.append(escaped);
                } else if (c == '"') {
                    return new BareItem.SfString(sb.toString());
                } else if (c < 0x20 || c > 0x7E) {
                    throw new StructuredFieldException(
                            "Invalid non-printable character 0x" + Integer.toHexString(c) + " in String", pos - 1);
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated String literal");
        }

        BareItem.SfToken parseToken() {
            int start = pos++;
            while (!isAtEnd() && BareItem.SfToken.isTokenChar(peek())) {
                pos++;
            }
            return new BareItem.SfToken(input.substring(start, pos));
        }

        BareItem.SfByteSequence parseByteSequence() {
            expectChar(':');
            int start = pos;
            while (!isAtEnd() && peek() != ':') {
                char c = peek();
                boolean validB64 = (c >= 'A' && c <= 'Z')
                        || (c >= 'a' && c <= 'z')
                        || (c >= '0' && c <= '9')
                        || c == '+'
                        || c == '/'
                        || c == '=';
                if (!validB64) {
                    throw error("Invalid base64 character '" + c + "' in Byte Sequence");
                }
                pos++;
            }
            if (isAtEnd()) {
                throw error("Unterminated Byte Sequence (missing closing ':')");
            }
            String b64 = input.substring(start, pos);
            pos++; // consume closing ':'
            try {
                byte[] decoded = Base64.getDecoder().decode(b64);
                return new BareItem.SfByteSequence(decoded);
            } catch (IllegalArgumentException e) {
                throw new StructuredFieldException("Malformed base64 in Byte Sequence: " + e.getMessage(), start);
            }
        }

        BareItem.SfBoolean parseBoolean() {
            expectChar('?');
            if (isAtEnd()) {
                throw error("Unexpected end of input after '?' in Boolean");
            }
            char c = advance();
            if (c == '1') {
                return BareItem.SfBoolean.TRUE;
            } else if (c == '0') {
                return BareItem.SfBoolean.FALSE;
            }
            throw new StructuredFieldException("Invalid boolean indicator '?" + c + "'", pos - 1);
        }
    }
}
