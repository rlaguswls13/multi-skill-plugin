package com.msp.interpolate;

/**
 * Thrown when an interpolated template contains an unresolved variable and
 * {@link MissingStrategy#THROW} is configured.
 */
public class MissingVariableException extends IllegalArgumentException {

    private final String variableName;

    public MissingVariableException(String variableName) {
        super("Unresolved template variable: '" + variableName + "'");
        this.variableName = variableName;
    }

    public String getVariableName() {
        return variableName;
    }
}
