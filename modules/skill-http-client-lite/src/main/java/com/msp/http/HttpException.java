package com.msp.http;

/**
 * Runtime exception thrown when an HTTP client request execution fails.
 */
public class HttpException extends RuntimeException {

    private final int statusCode;

    public HttpException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public HttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public HttpException(int statusCode, String message) {
        super("HTTP " + statusCode + ": " + message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
