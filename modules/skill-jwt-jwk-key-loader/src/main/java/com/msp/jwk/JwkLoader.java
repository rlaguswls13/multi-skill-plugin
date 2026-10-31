package com.msp.jwk;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure Java 21 loader and parser for RFC 7517 JSON Web Key (JWK) and JWK Set (JWKS).
 * Zero external JSON or cryptography dependencies.
 */
public final class JwkLoader {

    private JwkLoader() {
        // Utility class
    }

    /**
     * Parses an individual JWK JSON string and reconstructs the corresponding {@link PublicKey}.
     *
     * @param jwkJson raw JSON string of a single JWK object
     * @return reconstructed {@link JwkPublicKey}
     * @throws IllegalArgumentException if the JSON is malformed or key parameters are missing/invalid
     */
    public static JwkPublicKey loadJwk(String jwkJson) {
        Objects.requireNonNull(jwkJson, "jwkJson cannot be null");
        Object parsed = parseJson(jwkJson.trim());
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected JSON object for JWK, got: " + parsed);
        }
        return parseJwkMap(castToStringObjectMap(map));
    }

    /**
     * Parses a JWK Set (JWKS) JSON string (containing a "keys" array) into a map of keys.
     *
     * @param jwksJson raw JSON string of a JWKS document
     * @return Map of {@link JwkPublicKey} keyed by their 'kid' (or generated index if 'kid' is absent)
     * @throws IllegalArgumentException if the JWKS format is invalid
     */
    public static Map<String, JwkPublicKey> loadJwks(String jwksJson) {
        Objects.requireNonNull(jwksJson, "jwksJson cannot be null");
        Object parsed = parseJson(jwksJson.trim());
        if (!(parsed instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("Expected JSON object for JWKS, got: " + parsed);
        }

        Object keysObj = rootMap.get("keys");
        if (!(keysObj instanceof List<?> keysList)) {
            throw new IllegalArgumentException("JWKS JSON must contain a 'keys' array property");
        }

        Map<String, JwkPublicKey> resultMap = new LinkedHashMap<>();
        int index = 0;
        for (Object keyObj : keysList) {
            index++;
            if (!(keyObj instanceof Map<?, ?> keyMap)) {
                continue;
            }
            JwkPublicKey jwkPublicKey = parseJwkMap(castToStringObjectMap(keyMap));
            String keyId = (jwkPublicKey.kid() != null && !jwkPublicKey.kid().isBlank())
                    ? jwkPublicKey.kid()
                    : "key-" + index;
            resultMap.put(keyId, jwkPublicKey);
        }

        return Collections.unmodifiableMap(resultMap);
    }

    private static JwkPublicKey parseJwkMap(Map<String, Object> map) {
        String ktyStr = getString(map, "kty");
        if (ktyStr == null || ktyStr.isBlank()) {
            throw new IllegalArgumentException("Missing required 'kty' parameter in JWK");
        }

        JwkKeyType keyType = JwkKeyType.fromString(ktyStr);
        String kid = getString(map, "kid");
        String use = getString(map, "use");
        String alg = getString(map, "alg");

        PublicKey publicKey = switch (keyType) {
            case RSA -> createRsaPublicKey(map);
            case EC, OCT -> throw new UnsupportedOperationException(
                    "Public key reconstruction for " + keyType + " is not yet supported. Only RSA is currently supported."
            );
        };

        return new JwkPublicKey(kid, keyType.name(), use, alg, publicKey);
    }

    private static PublicKey createRsaPublicKey(Map<String, Object> map) {
        String nStr = getString(map, "n");
        String eStr = getString(map, "e");

        if (nStr == null || nStr.isBlank()) {
            throw new IllegalArgumentException("Missing required RSA modulus 'n' parameter");
        }
        if (eStr == null || eStr.isBlank()) {
            throw new IllegalArgumentException("Missing required RSA exponent 'e' parameter");
        }

        try {
            byte[] modulusBytes = Base64.getUrlDecoder().decode(nStr);
            byte[] exponentBytes = Base64.getUrlDecoder().decode(eStr);

            // Modulus and exponent in JWK are unsigned big-endian integers (signum = 1)
            BigInteger modulus = new BigInteger(1, modulusBytes);
            BigInteger exponent = new BigInteger(1, exponentBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64Url encoding for RSA parameters: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to construct RSA public key: " + e.getMessage(), e);
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : (val != null ? val.toString() : null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    // =========================================================================
    // Minimal Pure Java JSON Parser (RFC 8259 subset for JWK/JWKS)
    // =========================================================================

    static Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON string cannot be empty");
        }
        JsonTokenizer tokenizer = new JsonTokenizer(json);
        Object result = tokenizer.parseValue();
        tokenizer.skipWhitespace();
        if (tokenizer.hasMore()) {
            throw new IllegalArgumentException("Unexpected trailing tokens after JSON: " + tokenizer.remainder());
        }
        return result;
    }

    private static final class JsonTokenizer {
        private final String src;
        private int pos = 0;

        JsonTokenizer(String src) {
            this.src = src;
        }

        Object parseValue() {
            skipWhitespace();
            if (!hasMore()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> {
                    if (c == '-' || Character.isDigit(c)) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected character at " + pos + ": '" + c + "'");
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (hasMore() && peek() == '}') {
                consume();
                return map;
            }

            while (hasMore()) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new IllegalArgumentException("Expected string key at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);

                skipWhitespace();
                if (peek() == ',') {
                    consume();
                } else if (peek() == '}') {
                    consume();
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (hasMore() && peek() == ']') {
                consume();
                return list;
            }

            while (hasMore()) {
                Object item = parseValue();
                list.add(item);
                skipWhitespace();
                if (peek() == ',') {
                    consume();
                } else if (peek() == ']') {
                    consume();
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (hasMore()) {
                char c = consume();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (!hasMore()) {
                        throw new IllegalArgumentException("Unterminated escape sequence at " + pos);
                    }
                    char esc = consume();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Incomplete unicode escape at " + pos);
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Invalid escape sequence: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string literal at " + pos);
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null literal at position " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (peek() == '-') {
                consume();
            }
            while (hasMore() && (Character.isDigit(peek()) || peek() == '.' || peek() == 'e' || peek() == 'E' || peek() == '+' || peek() == '-')) {
                consume();
            }
            String raw = src.substring(start, pos);
            try {
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                    return Double.parseDouble(raw);
                }
                long val = Long.parseLong(raw);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return (int) val;
                }
                return val;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number at " + start + ": " + raw, e);
            }
        }

        private void skipWhitespace() {
            while (hasMore()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private boolean hasMore() {
            return pos < src.length();
        }

        private char peek() {
            return src.charAt(pos);
        }

        private char consume() {
            return src.charAt(pos++);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!hasMore() || peek() != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos + ", got '" + (hasMore() ? peek() : "EOF") + "'");
            }
            pos++;
        }

        private String remainder() {
            return src.substring(pos);
        }
    }
}
