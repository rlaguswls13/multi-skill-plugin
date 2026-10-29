package com.msp.tablecopier.api;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * 외부 JSON 라이브러리(Jackson/Gson) 의존성 없이도 API 응답 객체(Record, Map, List)를
 * 표준 JSON 문자열로 직렬화해주는 경량 포매터.
 */
public final class JsonResponseFormatter {

    private JsonResponseFormatter() {
    }

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String str) {
            writeEscapedString(sb, str);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Enum<?> en) {
            writeEscapedString(sb, en.name());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                writeEscapedString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                writeValue(sb, entry.getValue());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
        } else if (value instanceof Collection<?> col) {
            sb.append('[');
            Iterator<?> it = col.iterator();
            while (it.hasNext()) {
                writeValue(sb, it.next());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append(']');
        } else if (value.getClass().isRecord()) {
            sb.append('{');
            RecordComponent[] components = value.getClass().getRecordComponents();
            for (int i = 0; i < components.length; i++) {
                RecordComponent rc = components[i];
                writeEscapedString(sb, rc.getName());
                sb.append(':');
                try {
                    Object fieldVal = rc.getAccessor().invoke(value);
                    writeValue(sb, fieldVal);
                } catch (Exception e) {
                    sb.append("null");
                }
                if (i + 1 < components.length) {
                    sb.append(',');
                }
            }
            sb.append('}');
        } else {
            writeEscapedString(sb, String.valueOf(value));
        }
    }

    private static void writeEscapedString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }
}
