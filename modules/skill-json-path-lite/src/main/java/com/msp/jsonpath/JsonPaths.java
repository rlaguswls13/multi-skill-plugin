package com.msp.jsonpath;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Unified facade for JSONPath navigation and type extraction.
 */
public final class JsonPaths {

    private JsonPaths() {
    }

    public static Optional<Object> find(Object root, String path) {
        return JsonPath.find(root, path);
    }

    public static Optional<Object> find(String json, String path) {
        return JsonPath.find(json, path);
    }

    public static Optional<String> getString(Object root, String path) {
        return JsonPath.getString(root, path);
    }

    public static Optional<String> getString(String json, String path) {
        return JsonPath.getString(json, path);
    }

    public static Optional<Integer> getInt(Object root, String path) {
        return JsonPath.getInt(root, path);
    }

    public static Optional<Integer> getInt(String json, String path) {
        return JsonPath.getInt(json, path);
    }

    public static Optional<Long> getLong(Object root, String path) {
        return JsonPath.getLong(root, path);
    }

    public static Optional<Long> getLong(String json, String path) {
        return JsonPath.getLong(json, path);
    }

    public static Optional<Double> getDouble(Object root, String path) {
        return JsonPath.getDouble(root, path);
    }

    public static Optional<Double> getDouble(String json, String path) {
        return JsonPath.getDouble(json, path);
    }

    public static Optional<Boolean> getBool(Object root, String path) {
        return JsonPath.getBool(root, path);
    }

    public static Optional<Boolean> getBool(String json, String path) {
        return JsonPath.getBool(json, path);
    }

    public static List<Object> getList(Object root, String path) {
        return JsonPath.getList(root, path);
    }

    public static List<Object> getList(String json, String path) {
        return JsonPath.getList(json, path);
    }

    public static Optional<Map<String, Object>> getMap(Object root, String path) {
        return JsonPath.getMap(root, path);
    }

    public static Optional<Map<String, Object>> getMap(String json, String path) {
        return JsonPath.getMap(json, path);
    }

    public static boolean exists(Object root, String path) {
        return JsonPath.exists(root, path);
    }

    public static boolean exists(String json, String path) {
        return JsonPath.exists(json, path);
    }

    /**
     * Parses a raw JSON string into standard Map/List/primitive hierarchy.
     */
    public static Object parseJson(String json) {
        return MiniJson.parse(json);
    }
}
