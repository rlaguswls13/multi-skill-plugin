package com.msp.sqlinjection;

public class SqlInjectionException extends RuntimeException {
    public SqlInjectionException(String message) {
        super(message);
    }
}
