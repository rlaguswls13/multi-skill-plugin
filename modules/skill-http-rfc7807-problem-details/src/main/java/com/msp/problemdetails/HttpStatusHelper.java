package com.msp.problemdetails;

import java.util.Map;

/**
 * Standard HTTP status codes and canonical reason phrases according to RFC 9110.
 */
public final class HttpStatusHelper {

    private static final Map<Integer, String> REASON_PHRASES = Map.ofEntries(
            Map.entry(400, "Bad Request"),
            Map.entry(401, "Unauthorized"),
            Map.entry(402, "Payment Required"),
            Map.entry(403, "Forbidden"),
            Map.entry(404, "Not Found"),
            Map.entry(405, "Method Not Allowed"),
            Map.entry(406, "Not Acceptable"),
            Map.entry(407, "Proxy Authentication Required"),
            Map.entry(408, "Request Timeout"),
            Map.entry(409, "Conflict"),
            Map.entry(410, "Gone"),
            Map.entry(411, "Length Required"),
            Map.entry(412, "Precondition Failed"),
            Map.entry(413, "Content Too Large"),
            Map.entry(414, "URI Too Long"),
            Map.entry(415, "Unsupported Media Type"),
            Map.entry(416, "Range Not Satisfiable"),
            Map.entry(417, "Expectation Failed"),
            Map.entry(418, "I'm a teapot"),
            Map.entry(421, "Misdirected Request"),
            Map.entry(422, "Unprocessable Content"),
            Map.entry(423, "Locked"),
            Map.entry(424, "Failed Dependency"),
            Map.entry(426, "Upgrade Required"),
            Map.entry(428, "Precondition Required"),
            Map.entry(429, "Too Many Requests"),
            Map.entry(431, "Request Header Fields Too Large"),
            Map.entry(451, "Unavailable For Legal Reasons"),
            Map.entry(500, "Internal Server Error"),
            Map.entry(501, "Not Implemented"),
            Map.entry(502, "Bad Gateway"),
            Map.entry(503, "Service Unavailable"),
            Map.entry(504, "Gateway Timeout"),
            Map.entry(505, "HTTP Version Not Supported")
    );

    private HttpStatusHelper() {
    }

    /**
     * Returns default reason phrase for given HTTP status code, or fallback description.
     */
    public static String getReasonPhrase(int status) {
        String phrase = REASON_PHRASES.get(status);
        if (phrase != null) {
            return phrase;
        }
        if (status >= 400 && status < 500) {
            return "Client Error (" + status + ")";
        }
        if (status >= 500 && status < 600) {
            return "Server Error (" + status + ")";
        }
        return "HTTP Status " + status;
    }
}
