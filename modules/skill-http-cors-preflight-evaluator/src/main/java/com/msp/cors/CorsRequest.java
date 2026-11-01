package com.msp.cors;

import java.util.*;

/**
 * Represents an incoming HTTP request to be evaluated for CORS compliance.
 */
public final class CorsRequest {

    public static final String HEADER_ORIGIN = "Origin";
    public static final String HEADER_AC_REQUEST_METHOD = "Access-Control-Request-Method";
    public static final String HEADER_AC_REQUEST_HEADERS = "Access-Control-Request-Headers";
    public static final String HEADER_AC_REQUEST_PRIVATE_NETWORK = "Access-Control-Request-Private-Network";

    private final String method;
    private final String origin;
    private final String requestMethod;
    private final List<String> requestHeaders;
    private final boolean privateNetworkRequest;

    public CorsRequest(String method,
                       String origin,
                       String requestMethod,
                       List<String> requestHeaders,
                       boolean privateNetworkRequest) {
        this.method = method != null ? method.trim().toUpperCase(Locale.ROOT) : "GET";
        this.origin = origin != null && !origin.isBlank() ? origin.trim() : null;
        this.requestMethod = requestMethod != null && !requestMethod.isBlank() ? requestMethod.trim().toUpperCase(Locale.ROOT) : null;
        if (requestHeaders != null) {
            List<String> list = new ArrayList<>();
            for (String h : requestHeaders) {
                if (h != null && !h.isBlank()) {
                    list.add(h.trim());
                }
            }
            this.requestHeaders = Collections.unmodifiableList(list);
        } else {
            this.requestHeaders = Collections.emptyList();
        }
        this.privateNetworkRequest = privateNetworkRequest;
    }

    public static CorsRequest actual(String method, String origin) {
        return new CorsRequest(method, origin, null, Collections.emptyList(), false);
    }

    public static CorsRequest preflight(String origin, String requestMethod, List<String> requestHeaders) {
        return new CorsRequest("OPTIONS", origin, requestMethod, requestHeaders, false);
    }

    public static CorsRequest preflight(String origin, String requestMethod, String... requestHeaders) {
        return new CorsRequest("OPTIONS", origin, requestMethod,
                requestHeaders != null ? Arrays.asList(requestHeaders) : Collections.emptyList(), false);
    }

    /**
     * Parses a CorsRequest from standard HTTP headers map (case-insensitive keys).
     */
    public static CorsRequest fromHeaders(String method, Map<String, String> headers) {
        String origin = null;
        String reqMethod = null;
        List<String> reqHeaders = new ArrayList<>();
        boolean privateNet = false;

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null) continue;

                if (key.equalsIgnoreCase(HEADER_ORIGIN)) {
                    origin = val;
                } else if (key.equalsIgnoreCase(HEADER_AC_REQUEST_METHOD)) {
                    reqMethod = val;
                } else if (key.equalsIgnoreCase(HEADER_AC_REQUEST_HEADERS)) {
                    String[] parts = val.split(",");
                    for (String p : parts) {
                        if (!p.isBlank()) {
                            reqHeaders.add(p.trim());
                        }
                    }
                } else if (key.equalsIgnoreCase(HEADER_AC_REQUEST_PRIVATE_NETWORK)) {
                    privateNet = Boolean.parseBoolean(val.trim());
                }
            }
        }

        return new CorsRequest(method, origin, reqMethod, reqHeaders, privateNet);
    }

    public String getMethod() {
        return method;
    }

    public String getOrigin() {
        return origin;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public List<String> getRequestHeaders() {
        return requestHeaders;
    }

    public boolean isPrivateNetworkRequest() {
        return privateNetworkRequest;
    }

    public boolean isCorsRequest() {
        return origin != null && !origin.isBlank();
    }

    public boolean isPreflightRequest() {
        return "OPTIONS".equalsIgnoreCase(method) && isCorsRequest() && requestMethod != null && !requestMethod.isBlank();
    }

    @Override
    public String toString() {
        return "CorsRequest{" +
                "method='" + method + '\'' +
                ", origin='" + origin + '\'' +
                ", requestMethod='" + requestMethod + '\'' +
                ", requestHeaders=" + requestHeaders +
                ", privateNetworkRequest=" + privateNetworkRequest +
                '}';
    }
}
