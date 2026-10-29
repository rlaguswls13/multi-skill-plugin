package com.msp.diff;

import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * Recursive diff detector comparing nested Maps, Lists, Java Records, and scalar objects.
 */
public final class DiffDetector {

    private DiffDetector() {
    }

    /**
     * Compares two objects recursively and returns detected field differences.
     *
     * @param oldObj original object
     * @param newObj modified object
     * @return DiffResult containing ordered list of FieldChange entries
     */
    public static DiffResult compare(Object oldObj, Object newObj) {
        List<FieldChange> changes = new ArrayList<>();
        compareRecursive("", oldObj, newObj, changes);
        return new DiffResult(changes);
    }

    @SuppressWarnings("unchecked")
    private static void compareRecursive(String path, Object oldVal, Object newVal, List<FieldChange> changes) {
        if (Objects.equals(oldVal, newVal)) {
            return;
        }

        if (oldVal == null && newVal != null) {
            changes.add(new FieldChange(ChangeType.ADDED, path, null, newVal));
            return;
        }
        if (oldVal != null && newVal == null) {
            changes.add(new FieldChange(ChangeType.REMOVED, path, oldVal, null));
            return;
        }

        // Convert Java Records to Maps for structural inspection
        Object normalizedOld = normalizeRecord(oldVal);
        Object normalizedNew = normalizeRecord(newVal);

        // Map vs Map
        if (normalizedOld instanceof Map<?, ?> oldMap && normalizedNew instanceof Map<?, ?> newMap) {
            compareMaps(path, (Map<String, Object>) oldMap, (Map<String, Object>) newMap, changes);
            return;
        }

        // List vs List
        if (normalizedOld instanceof List<?> oldList && normalizedNew instanceof List<?> newList) {
            compareLists(path, oldList, newList, changes);
            return;
        }

        // Different types or scalar value change
        changes.add(new FieldChange(ChangeType.MODIFIED, path, oldVal, newVal));
    }

    private static void compareMaps(String parentPath, Map<String, Object> oldMap, Map<String, Object> newMap, List<FieldChange> changes) {
        Set<String> allKeys = new TreeSet<>();
        for (Object k : oldMap.keySet()) {
            allKeys.add(String.valueOf(k));
        }
        for (Object k : newMap.keySet()) {
            allKeys.add(String.valueOf(k));
        }

        for (String key : allKeys) {
            String fieldPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            boolean inOld = oldMap.containsKey(key);
            boolean inNew = newMap.containsKey(key);

            if (!inOld && inNew) {
                changes.add(new FieldChange(ChangeType.ADDED, fieldPath, null, newMap.get(key)));
            } else if (inOld && !inNew) {
                changes.add(new FieldChange(ChangeType.REMOVED, fieldPath, oldMap.get(key), null));
            } else {
                compareRecursive(fieldPath, oldMap.get(key), newMap.get(key), changes);
            }
        }
    }

    private static void compareLists(String parentPath, List<?> oldList, List<?> newList, List<FieldChange> changes) {
        int oldSize = oldList.size();
        int newSize = newList.size();
        int minSize = Math.min(oldSize, newSize);

        for (int i = 0; i < minSize; i++) {
            String itemPath = parentPath + "[" + i + "]";
            compareRecursive(itemPath, oldList.get(i), newList.get(i), changes);
        }

        if (newSize > oldSize) {
            for (int i = oldSize; i < newSize; i++) {
                String itemPath = parentPath + "[" + i + "]";
                changes.add(new FieldChange(ChangeType.ADDED, itemPath, null, newList.get(i)));
            }
        } else if (oldSize > newSize) {
            for (int i = newSize; i < oldSize; i++) {
                String itemPath = parentPath + "[" + i + "]";
                changes.add(new FieldChange(ChangeType.REMOVED, itemPath, oldList.get(i), null));
            }
        }
    }

    private static Object normalizeRecord(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj.getClass().isRecord()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (RecordComponent rc : obj.getClass().getRecordComponents()) {
                try {
                    map.put(rc.getName(), rc.getAccessor().invoke(obj));
                } catch (Exception ignored) {
                }
            }
            return map;
        }
        return obj;
    }
}
