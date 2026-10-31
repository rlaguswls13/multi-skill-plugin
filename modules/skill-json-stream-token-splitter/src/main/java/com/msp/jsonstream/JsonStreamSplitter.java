package com.msp.jsonstream;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Memory-efficient streaming token splitter for massive NDJSON (JSON Lines) streams
 * and gigantic top-level JSON arrays.
 *
 * <p>Streams elements without loading the entire payload into heap memory,
 * safely tracking string quotes, escape sequences, and object/array nesting depths.</p>
 */
public final class JsonStreamSplitter {

    private JsonStreamSplitter() {}

    /**
     * Splits a stream of Newline Delimited JSON (NDJSON) line-by-line, passing each
     * non-empty JSON line to the provided consumer.
     *
     * @param in                 InputStream containing NDJSON data
     * @param jsonObjectConsumer Consumer receiving each JSON object/line string
     */
    public static void splitNdjson(InputStream in, Consumer<String> jsonObjectConsumer) {
        splitNdjson(in, StandardCharsets.UTF_8, jsonObjectConsumer);
    }

    /**
     * Splits a stream of Newline Delimited JSON (NDJSON) line-by-line using the specified charset.
     *
     * @param in                 InputStream containing NDJSON data
     * @param charset            Character encoding of the stream
     * @param jsonObjectConsumer Consumer receiving each JSON object/line string
     */
    public static void splitNdjson(InputStream in, Charset charset, Consumer<String> jsonObjectConsumer) {
        Objects.requireNonNull(in, "InputStream must not be null");
        Objects.requireNonNull(charset, "Charset must not be null");
        Objects.requireNonNull(jsonObjectConsumer, "Consumer must not be null");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    jsonObjectConsumer.accept(trimmed);
                }
            }
        } catch (IOException e) {
            throw new JsonStreamException("I/O error while streaming NDJSON", e);
        }
    }

    /**
     * Splits a top-level JSON array (e.g. {@code [ {..}, {..}, .. ]}) streaming element-by-element
     * without buffering more than one element in memory at a time.
     *
     * @param in              InputStream containing the JSON array
     * @param elementConsumer Consumer receiving each individual element string
     */
    public static void splitTopLevelArray(InputStream in, Consumer<String> elementConsumer) {
        splitTopLevelArray(in, StandardCharsets.UTF_8, elementConsumer);
    }

    /**
     * Splits a top-level JSON array streaming element-by-element using the specified charset.
     *
     * @param in              InputStream containing the JSON array
     * @param charset         Character encoding of the stream
     * @param elementConsumer Consumer receiving each individual element string
     */
    public static void splitTopLevelArray(InputStream in, Charset charset, Consumer<String> elementConsumer) {
        Objects.requireNonNull(in, "InputStream must not be null");
        Objects.requireNonNull(charset, "Charset must not be null");
        Objects.requireNonNull(elementConsumer, "Consumer must not be null");

        try (PushbackReader reader = new PushbackReader(new BufferedReader(new InputStreamReader(in, charset)), 2)) {
            // 1. Locate opening bracket '['
            int ch;
            while ((ch = reader.read()) != -1) {
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                if (ch == '[') {
                    break;
                }
                throw new JsonStreamException("Expected start of JSON array '[', found: '" + (char) ch + "'");
            }

            if (ch == -1) {
                // Empty stream, nothing to do
                return;
            }

            // 2. Stream individual elements
            while (true) {
                // Skip whitespace and element separators (commas)
                while ((ch = reader.read()) != -1) {
                    if (!Character.isWhitespace(ch) && ch != ',') {
                        break;
                    }
                }

                if (ch == -1 || ch == ']') {
                    // Reached end of array
                    break;
                }

                // Start of an element
                StringBuilder elementBuf = new StringBuilder();
                elementBuf.append((char) ch);

                boolean inString = (ch == '"');
                boolean escaped = false;
                int depth = 0;
                if (ch == '{' || ch == '[') {
                    depth = 1;
                }

                boolean elementComplete = false;

                // If single-char string (e.g. empty string "") or depth 0 scalar
                while (!elementComplete && (ch = reader.read()) != -1) {
                    char c = (char) ch;

                    if (inString) {
                        elementBuf.append(c);
                        if (escaped) {
                            escaped = false;
                        } else if (c == '\\') {
                            escaped = true;
                        } else if (c == '"') {
                            inString = false;
                            if (depth == 0) {
                                // Top-level string scalar completed
                                elementComplete = true;
                            }
                        }
                    } else {
                        if (c == '"') {
                            inString = true;
                            elementBuf.append(c);
                        } else if (c == '{' || c == '[') {
                            depth++;
                            elementBuf.append(c);
                        } else if (c == '}' || c == ']') {
                            if (depth > 0) {
                                depth--;
                                elementBuf.append(c);
                                if (depth == 0) {
                                    elementComplete = true;
                                }
                            } else {
                                // Hit top-level array closing ']' while accumulating a scalar!
                                reader.unread(ch);
                                elementComplete = true;
                            }
                        } else if (depth == 0 && (c == ',' || Character.isWhitespace(c))) {
                            // Scalar (number/boolean/null) completed
                            if (c == ',') {
                                reader.unread(ch);
                            }
                            elementComplete = true;
                        } else {
                            elementBuf.append(c);
                        }
                    }
                }

                String elementStr = elementBuf.toString().trim();
                if (!elementStr.isEmpty()) {
                    elementConsumer.accept(elementStr);
                }
            }
        } catch (IOException e) {
            throw new JsonStreamException("I/O error while streaming JSON array", e);
        }
    }

    /**
     * Convenience method to split NDJSON into an in-memory List.
     */
    public static List<String> splitNdjsonToList(InputStream in) {
        List<String> list = new ArrayList<>();
        splitNdjson(in, list::add);
        return list;
    }

    /**
     * Convenience method to split a top-level array into an in-memory List.
     */
    public static List<String> splitTopLevelArrayToList(InputStream in) {
        List<String> list = new ArrayList<>();
        splitTopLevelArray(in, list::add);
        return list;
    }
}
