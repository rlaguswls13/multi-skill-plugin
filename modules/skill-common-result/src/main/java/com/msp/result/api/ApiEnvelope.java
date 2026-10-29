package com.msp.result.api;

import com.msp.result.Result;
import com.msp.result.error.CommonErrorCode;
import com.msp.result.error.ErrorCode;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * 외부 REST API 또는 모듈 간 응답을 통일하는 표준 JSON 호환 엔벨로프.
 *
 * @param <T> 응답 본문 데이터 타입
 */
public record ApiEnvelope<T>(
        boolean success,
        int status,
        String code,
        String message,
        T data,
        Object error,
        String timestamp
) {
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(
                true,
                CommonErrorCode.SUCCESS.httpStatus(),
                CommonErrorCode.SUCCESS.code(),
                CommonErrorCode.SUCCESS.message(),
                data,
                null,
                Instant.now().toString()
        );
    }

    public static <T> ApiEnvelope<T> error(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode);
        return new ApiEnvelope<>(
                false,
                errorCode.httpStatus(),
                errorCode.code(),
                errorCode.message(),
                null,
                null,
                Instant.now().toString()
        );
    }

    public static <T> ApiEnvelope<T> error(ErrorCode errorCode, Object errorDetail) {
        Objects.requireNonNull(errorCode);
        return new ApiEnvelope<>(
                false,
                errorCode.httpStatus(),
                errorCode.code(),
                errorCode.message(),
                null,
                errorDetail,
                Instant.now().toString()
        );
    }

    public static <T> ApiEnvelope<T> fromResult(Result<T, ? extends ErrorCode> result) {
        Objects.requireNonNull(result);
        if (result.isSuccess()) {
            return ok(result.getOrNull());
        }
        return error(result.getErrorOrNull());
    }

    /**
     * 외부 라이브러리(Jackson/Gson) 없이 순수 Java로 JSON 직렬화 수행
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, this);
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
                    var accessor = rc.getAccessor();
                    if (!accessor.canAccess(value)) {
                        accessor.setAccessible(true);
                    }
                    Object fieldVal = accessor.invoke(value);
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
