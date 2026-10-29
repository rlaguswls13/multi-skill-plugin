package com.msp.fixedlen;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Schema defining fixed-length record fields, byte lengths, alignments, and padding.
 * Thread-safe and immutable.
 */
public final class FixedLengthSchema {

    private final Charset charset;
    private final List<FixedLengthField> fields;
    private final int totalByteLength;
    private final Map<String, FixedLengthField> fieldMap;

    private FixedLengthSchema(Builder builder) {
        this.charset = builder.charset;
        this.fields = List.copyOf(builder.fields);
        this.totalByteLength = fields.stream().mapToInt(FixedLengthField::byteLength).sum();

        Map<String, FixedLengthField> map = new LinkedHashMap<>();
        for (FixedLengthField f : this.fields) {
            map.put(f.name(), f);
        }
        this.fieldMap = Collections.unmodifiableMap(map);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Charset getCharset() {
        return charset;
    }

    public List<FixedLengthField> getFields() {
        return fields;
    }

    public int getTotalByteLength() {
        return totalByteLength;
    }

    public boolean hasField(String name) {
        return fieldMap.containsKey(name);
    }

    public FixedLengthField getField(String name) {
        FixedLengthField field = fieldMap.get(name);
        if (field == null) {
            throw new FixedLengthException("Field '" + name + "' not found in schema");
        }
        return field;
    }

    /**
     * Packs field values map into a fixed-length byte array.
     */
    public byte[] packToBytes(Map<String, ?> data) {
        byte[] buffer = new byte[totalByteLength];
        int offset = 0;

        for (FixedLengthField field : fields) {
            Object rawVal = (data != null) ? data.get(field.name()) : null;
            String text = (rawVal != null) ? String.valueOf(rawVal) : field.defaultValue();
            if (text == null) {
                text = "";
            }

            byte[] fieldBytes = encodeAndPad(text, field);
            System.arraycopy(fieldBytes, 0, buffer, offset, field.byteLength());
            offset += field.byteLength();
        }

        return buffer;
    }

    /**
     * Packs field values map into a fixed-length string using the schema charset.
     */
    public String pack(Map<String, ?> data) {
        return new String(packToBytes(data), charset);
    }

    /**
     * Unpacks a fixed-length byte array into a FixedLengthRecord.
     */
    public FixedLengthRecord unpack(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length < totalByteLength) {
            throw new FixedLengthException(
                    "Input bytes length (" + bytes.length + ") is shorter than schema length (" + totalByteLength + ")"
            );
        }

        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> rawValues = new LinkedHashMap<>();
        int offset = 0;

        for (FixedLengthField field : fields) {
            byte[] slice = Arrays.copyOfRange(bytes, offset, offset + field.byteLength());
            String rawStr = new String(slice, charset);
            rawValues.put(field.name(), rawStr);
            values.put(field.name(), stripPadding(rawStr, field.alignment(), field.paddingChar()));
            offset += field.byteLength();
        }

        return new FixedLengthRecord(this, values, rawValues);
    }

    /**
     * Unpacks a raw fixed-length string into a FixedLengthRecord.
     */
    public FixedLengthRecord unpack(String raw) {
        Objects.requireNonNull(raw, "raw string must not be null");
        return unpack(raw.getBytes(charset));
    }

    /**
     * Unpacks a raw fixed-length string directly into a Map.
     */
    public Map<String, String> unpackToMap(String raw) {
        return unpack(raw).toMap();
    }

    private byte[] encodeAndPad(String text, FixedLengthField field) {
        int targetLen = field.byteLength();
        byte[] valBytes = text.getBytes(charset);

        if (valBytes.length > targetLen) {
            if (!field.truncateOnOverflow()) {
                throw new FixedLengthException(
                        "Value byte length (" + valBytes.length + ") exceeds field '" + field.name() +
                                "' byte length (" + targetLen + ")"
                );
            }
            // Safely truncate at multi-byte character boundary
            valBytes = truncateSafely(text, targetLen);
        }

        byte[] result = new byte[targetLen];
        byte padByte = getPadByte(field.paddingChar());
        int padCount = targetLen - valBytes.length;

        if (field.alignment() == Alignment.LEFT) {
            System.arraycopy(valBytes, 0, result, 0, valBytes.length);
            Arrays.fill(result, valBytes.length, targetLen, padByte);
        } else {
            Arrays.fill(result, 0, padCount, padByte);
            System.arraycopy(valBytes, 0, result, padCount, valBytes.length);
        }

        return result;
    }

    private byte[] truncateSafely(String text, int maxBytes) {
        byte[] lastValid = new byte[0];
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int step = Character.charCount(codePoint);
            String candidate = text.substring(0, i + step);
            byte[] candBytes = candidate.getBytes(charset);
            if (candBytes.length > maxBytes) {
                break;
            }
            lastValid = candBytes;
            i += step;
        }
        return lastValid;
    }

    private byte getPadByte(char padChar) {
        byte[] b = String.valueOf(padChar).getBytes(charset);
        return (b.length > 0) ? b[0] : (byte) ' ';
    }

    private static String stripPadding(String raw, Alignment alignment, char padChar) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (alignment == Alignment.LEFT) {
            int end = raw.length();
            while (end > 0 && raw.charAt(end - 1) == padChar) {
                end--;
            }
            return raw.substring(0, end);
        } else {
            int start = 0;
            while (start < raw.length() && raw.charAt(start) == padChar) {
                start++;
            }
            if (start == raw.length()) {
                return (padChar == '0') ? "0" : "";
            }
            return raw.substring(start);
        }
    }

    public static final class Builder {
        private Charset charset = StandardCharsets.UTF_8;
        private final List<FixedLengthField> fields = new ArrayList<>();

        public Builder charset(Charset charset) {
            this.charset = Objects.requireNonNull(charset, "charset must not be null");
            return this;
        }

        public Builder addField(FixedLengthField field) {
            fields.add(Objects.requireNonNull(field, "field must not be null"));
            return this;
        }

        public Builder addField(String name, int byteLength, Alignment alignment, char paddingChar) {
            return addField(new FixedLengthField(name, byteLength, alignment, paddingChar, "", true));
        }

        public Builder addField(String name, int byteLength, Alignment alignment, char paddingChar, boolean truncateOnOverflow) {
            return addField(new FixedLengthField(name, byteLength, alignment, paddingChar, "", truncateOnOverflow));
        }

        public Builder addField(String name, int byteLength) {
            return addField(new FixedLengthField(name, byteLength, Alignment.LEFT, ' ', "", true));
        }

        public FixedLengthSchema build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("FixedLengthSchema must contain at least one field");
            }
            return new FixedLengthSchema(this);
        }
    }
}
