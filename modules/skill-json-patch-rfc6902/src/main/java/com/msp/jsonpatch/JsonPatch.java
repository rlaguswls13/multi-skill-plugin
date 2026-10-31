package com.msp.jsonpatch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 6902 JSON Patch processor.
 * <p>
 * Modifies documents represented using standard Java {@link Map} and {@link List} instances.
 * Supported operations: {@code add}, {@code remove}, {@code replace}, {@code move}, {@code copy}, {@code test}.
 * </p>
 */
public final class JsonPatch {

    private JsonPatch() {
        // Utility class
    }

    /**
     * Applies a list of JSON Patch operations in sequence to the root document.
     *
     * @param root       the document root (typically {@code Map<String, Object>} or {@code List<Object>})
     * @param operations ordered list of patch operations
     * @return the resulting document root (may be the mutated root or a replaced root)
     * @throws JsonPatchException if any operation cannot be performed
     */
    public static Object apply(Object root, List<JsonPatchOperation> operations) {
        Objects.requireNonNull(operations, "operations must not be null");

        Object currentRoot = root;
        for (JsonPatchOperation op : operations) {
            currentRoot = applySingle(currentRoot, op);
        }
        return currentRoot;
    }

    private static Object applySingle(Object root, JsonPatchOperation op) {
        JsonPointer targetPointer = JsonPointer.of(op.path());

        return switch (op.op()) {
            case ADD -> performAdd(root, targetPointer, op.value());
            case REMOVE -> performRemove(root, targetPointer);
            case REPLACE -> performReplace(root, targetPointer, op.value());
            case MOVE -> performMove(root, JsonPointer.of(op.from()), targetPointer);
            case COPY -> performCopy(root, JsonPointer.of(op.from()), targetPointer);
            case TEST -> performTest(root, targetPointer, op.value());
        };
    }

    @SuppressWarnings("unchecked")
    private static Object performAdd(Object root, JsonPointer path, Object value) {
        if (path.isRoot()) {
            return value;
        }

        JsonPointer parentPointer = path.parent();
        Object parent = parentPointer.evaluate(root);
        String lastToken = path.lastToken();

        if (parent instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) parent;
            map.put(lastToken, value);
        } else if (parent instanceof List<?>) {
            List<Object> list = (List<Object>) parent;
            if (lastToken.equals("-")) {
                list.add(value);
            } else {
                int index = JsonPointer.parseIndex(lastToken, list.size());
                if (index < 0 || index > list.size()) {
                    throw new JsonPatchException("Index out of bounds for add: " + index + ", list size: " + list.size());
                }
                list.add(index, value);
            }
        } else {
            throw new JsonPatchException("Cannot add to non-container parent: " + (parent != null ? parent.getClass().getSimpleName() : "null"));
        }

        return root;
    }

    @SuppressWarnings("unchecked")
    private static Object performRemove(Object root, JsonPointer path) {
        if (path.isRoot()) {
            throw new JsonPatchException("Cannot remove root document");
        }

        JsonPointer parentPointer = path.parent();
        Object parent = parentPointer.evaluate(root);
        String lastToken = path.lastToken();

        if (parent instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) parent;
            if (!map.containsKey(lastToken)) {
                throw new JsonPatchException("Key not found for remove: '" + lastToken + "' at " + path);
            }
            map.remove(lastToken);
        } else if (parent instanceof List<?>) {
            List<Object> list = (List<Object>) parent;
            if (lastToken.equals("-")) {
                throw new JsonPatchException("Cannot remove '-' from list: " + path);
            }
            int index = JsonPointer.parseIndex(lastToken, list.size());
            if (index < 0 || index >= list.size()) {
                throw new JsonPatchException("Index out of bounds for remove: " + index + ", list size: " + list.size());
            }
            list.remove(index);
        } else {
            throw new JsonPatchException("Cannot remove from non-container parent");
        }

        return root;
    }

    @SuppressWarnings("unchecked")
    private static Object performReplace(Object root, JsonPointer path, Object value) {
        if (path.isRoot()) {
            return value;
        }

        JsonPointer parentPointer = path.parent();
        Object parent = parentPointer.evaluate(root);
        String lastToken = path.lastToken();

        if (parent instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) parent;
            if (!map.containsKey(lastToken)) {
                throw new JsonPatchException("Key not found for replace: '" + lastToken + "' at " + path);
            }
            map.put(lastToken, value);
        } else if (parent instanceof List<?>) {
            List<Object> list = (List<Object>) parent;
            int index = JsonPointer.parseIndex(lastToken, list.size());
            if (index < 0 || index >= list.size()) {
                throw new JsonPatchException("Index out of bounds for replace: " + index + ", list size: " + list.size());
            }
            list.set(index, value);
        } else {
            throw new JsonPatchException("Cannot replace in non-container parent");
        }

        return root;
    }

    private static Object performMove(Object root, JsonPointer from, JsonPointer to) {
        if (to.isProperChildOf(from)) {
            throw new JsonPatchException("Target location '" + to + "' cannot be a proper child of 'from' location '" + from + "'");
        }

        if (from.equals(to)) {
            return root;
        }

        Object valueToMove = from.evaluate(root);
        Object modifiedRoot = performRemove(root, from);
        return performAdd(modifiedRoot, to, valueToMove);
    }

    private static Object performCopy(Object root, JsonPointer from, JsonPointer to) {
        Object valueToCopy = from.evaluate(root);
        Object cloned = deepClone(valueToCopy);
        return performAdd(root, to, cloned);
    }

    private static Object performTest(Object root, JsonPointer path, Object expected) {
        Object actual = path.evaluate(root);
        if (!deepEquals(expected, actual)) {
            throw new JsonPatchTestFailedException(path.raw(), expected, actual);
        }
        return root;
    }

    /**
     * Performs a deep clone of standard JSON structures (Map, List, scalars).
     */
    public static Object deepClone(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepClone(entry.getValue()));
            }
            return copy;
        } else if (obj instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepClone(item));
            }
            return copy;
        }
        return obj;
    }

    /**
     * Performs recursive deep structural comparison of JSON values.
     */
    public static boolean deepEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }

        if (a instanceof Number n1 && b instanceof Number n2) {
            return Double.compare(n1.doubleValue(), n2.doubleValue()) == 0;
        }

        if (a instanceof Map<?, ?> m1 && b instanceof Map<?, ?> m2) {
            if (m1.size() != m2.size()) {
                return false;
            }
            for (Map.Entry<?, ?> entry : m1.entrySet()) {
                if (!m2.containsKey(entry.getKey())) {
                    return false;
                }
                if (!deepEquals(entry.getValue(), m2.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        if (a instanceof List<?> l1 && b instanceof List<?> l2) {
            if (l1.size() != l2.size()) {
                return false;
            }
            for (int i = 0; i < l1.size(); i++) {
                if (!deepEquals(l1.get(i), l2.get(i))) {
                    return false;
                }
            }
            return true;
        }

        return Objects.equals(a, b);
    }
}
