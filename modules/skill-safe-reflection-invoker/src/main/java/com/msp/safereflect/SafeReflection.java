package com.msp.safereflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Fluent, unchecked-exception safe Java reflection utility with reflective member caching.
 */
public final class SafeReflection {

    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private final Object target;
    private final Class<?> targetClass;

    private SafeReflection(Object target, Class<?> targetClass) {
        this.target = target;
        this.targetClass = targetClass;
    }

    public static SafeReflection of(Object target) {
        Objects.requireNonNull(target, "target must not be null");
        return new SafeReflection(target, target.getClass());
    }

    public static SafeReflection ofClass(Class<?> targetClass) {
        Objects.requireNonNull(targetClass, "targetClass must not be null");
        return new SafeReflection(null, targetClass);
    }

    /**
     * Instantiates a new object of the given class using matching constructor.
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clazz, Object... args) {
        Objects.requireNonNull(clazz, "clazz must not be null");
        try {
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                if (matchesParameters(ctor.getParameterTypes(), args)) {
                    ctor.setAccessible(true);
                    return (T) ctor.newInstance(args);
                }
            }
            throw new SafeReflectionException("No suitable constructor found for " + clazz.getName() + " with args " + Arrays.toString(args));
        } catch (SafeReflectionException e) {
            throw e;
        } catch (Exception e) {
            throw new SafeReflectionException("Failed to instantiate " + clazz.getName(), e);
        }
    }

    /**
     * Reads a field value (including private and inherited fields).
     */
    @SuppressWarnings("unchecked")
    public <T> T getFieldValue(String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        try {
            Field field = findField(targetClass, fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception e) {
            throw new SafeReflectionException("Failed to get field '" + fieldName + "' on " + targetClass.getName(), e);
        }
    }

    /**
     * Writes a value to a field (including private and inherited fields).
     */
    public SafeReflection setFieldValue(String fieldName, Object value) {
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        try {
            Field field = findField(targetClass, fieldName);
            field.setAccessible(true);
            field.set(target, value);
            return this;
        } catch (Exception e) {
            throw new SafeReflectionException("Failed to set field '" + fieldName + "' on " + targetClass.getName(), e);
        }
    }

    /**
     * Invokes a method by name and arguments (including private methods).
     */
    @SuppressWarnings("unchecked")
    public <T> T invokeMethod(String methodName, Object... args) {
        Objects.requireNonNull(methodName, "methodName must not be null");
        try {
            Method method = findMethod(targetClass, methodName, args);
            method.setAccessible(true);
            return (T) method.invoke(target, args);
        } catch (Exception e) {
            throw new SafeReflectionException("Failed to invoke method '" + methodName + "' on " + targetClass.getName(), e);
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        String cacheKey = clazz.getName() + "#" + fieldName;
        return FIELD_CACHE.computeIfAbsent(cacheKey, key -> {
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new SafeReflectionException("Field '" + fieldName + "' not found in hierarchy of " + clazz.getName());
        });
    }

    private static Method findMethod(Class<?> clazz, String methodName, Object[] args) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && matchesParameters(m.getParameterTypes(), args)) {
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        throw new SafeReflectionException("Method '" + methodName + "' with compatible parameters not found in " + clazz.getName());
    }

    private static boolean matchesParameters(Class<?>[] paramTypes, Object[] args) {
        if (paramTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < paramTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (paramTypes[i].isPrimitive()) return false;
            } else {
                Class<?> pType = wrapPrimitive(paramTypes[i]);
                if (!pType.isAssignableFrom(arg.getClass())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> clazz) {
        if (!clazz.isPrimitive()) return clazz;
        if (clazz == int.class) return Integer.class;
        if (clazz == long.class) return Long.class;
        if (clazz == double.class) return Double.class;
        if (clazz == float.class) return Float.class;
        if (clazz == boolean.class) return Boolean.class;
        if (clazz == byte.class) return Byte.class;
        if (clazz == char.class) return Character.class;
        if (clazz == short.class) return Short.class;
        return clazz;
    }
}
