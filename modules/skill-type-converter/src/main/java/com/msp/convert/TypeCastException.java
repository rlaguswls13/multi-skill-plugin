package com.msp.convert;

/**
 * Exception thrown when a type conversion fails in strict mode.
 */
public class TypeCastException extends RuntimeException {

    private final String rawValue;
    private final Class<?> targetType;

    public TypeCastException(String rawValue, Class<?> targetType) {
        super(String.format("Cannot convert '%s' to %s", rawValue, targetType.getSimpleName()));
        this.rawValue = rawValue;
        this.targetType = targetType;
    }

    public TypeCastException(String rawValue, Class<?> targetType, Throwable cause) {
        super(String.format("Cannot convert '%s' to %s: %s", rawValue, targetType.getSimpleName(), cause.getMessage()), cause);
        this.rawValue = rawValue;
        this.targetType = targetType;
    }

    public String getRawValue() {
        return rawValue;
    }

    public Class<?> getTargetType() {
        return targetType;
    }
}
