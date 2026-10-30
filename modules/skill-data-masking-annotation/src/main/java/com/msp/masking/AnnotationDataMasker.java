package com.msp.masking;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * 리플렉션을 통해 @Mask 어노테이션이 붙은 필드를 자동 감지하여
 * 마스킹된 Map 또는 안전한 로깅 문자열을 생성하는 마스커.
 */
public final class AnnotationDataMasker {

    private AnnotationDataMasker() {}

    public static Map<String, Object> toMaskedMap(Object target) {
        if (target == null) return Collections.emptyMap();
        Map<String, Object> map = new LinkedHashMap<>();

        Class<?> clazz = target.getClass();

        if (clazz.isRecord()) {
            for (RecordComponent rc : clazz.getRecordComponents()) {
                try {
                    Object val = rc.getAccessor().invoke(target);
                    Mask mask = rc.getAnnotation(Mask.class);
                    if (mask == null) {
                        // Check backing field
                        try {
                            Field f = clazz.getDeclaredField(rc.getName());
                            mask = f.getAnnotation(Mask.class);
                        } catch (Exception ignored) {}
                    }
                    map.put(rc.getName(), applyMask(val, mask));
                } catch (Exception ignored) {}
            }
            return map;
        }

        // Standard Class hierarchy
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(target);
                    Mask mask = f.getAnnotation(Mask.class);
                    map.put(f.getName(), applyMask(val, mask));
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }

        return map;
    }

    public static String toMaskedString(Object target) {
        if (target == null) return "null";
        Map<String, Object> map = toMaskedMap(target);
        StringBuilder sb = new StringBuilder();
        sb.append(target.getClass().getSimpleName()).append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    private static Object applyMask(Object val, Mask mask) {
        if (mask == null || val == null) return val;
        String str = val.toString();

        return switch (mask.type()) {
            case NAME -> maskName(str, mask.maskChar());
            case PHONE -> maskPhone(str, mask.maskChar());
            case EMAIL -> maskEmail(str, mask.maskChar());
            case CARD -> maskCard(str, mask.maskChar());
            case CUSTOM -> maskCustom(str, mask.prefixKeep(), mask.suffixKeep(), mask.maskChar());
        };
    }

    private static String maskName(String name, char c) {
        if (name.length() <= 1) return name;
        if (name.length() == 2) return name.charAt(0) + String.valueOf(c);
        return name.charAt(0) + String.valueOf(c).repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    private static String maskPhone(String phone, char c) {
        String clean = phone.replaceAll("[^0-9]", "");
        if (clean.length() == 11) { // 010-1234-5678
            return phone.replaceAll("(\\d{3})[-.]?(\\d{4})[-.]?(\\d{4})", "$1-" + String.valueOf(c).repeat(4) + "-$3");
        }
        return maskCustom(phone, 3, 4, c);
    }

    private static String maskEmail(String email, char c) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        String id = email.substring(0, at);
        String domain = email.substring(at);
        return id.charAt(0) + String.valueOf(c).repeat(Math.max(1, id.length() - 1)) + domain;
    }

    private static String maskCard(String card, char c) {
        return card.replaceAll("(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})", "$1-" + String.valueOf(c).repeat(4) + "-" + String.valueOf(c).repeat(4) + "-$4");
    }

    private static String maskCustom(String text, int prefixKeep, int suffixKeep, char c) {
        int len = text.length();
        if (len <= prefixKeep + suffixKeep) {
            return String.valueOf(c).repeat(len);
        }
        return text.substring(0, prefixKeep) + String.valueOf(c).repeat(len - prefixKeep - suffixKeep) + text.substring(len - suffixKeep);
    }
}
