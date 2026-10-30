package com.msp.multiupload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent builder creating standard {@link HttpRequest.BodyPublisher} for {@code multipart/form-data} uploads.
 * <p>
 * Compliant with RFC 7578 / RFC 2046 specifications using pure Java 21 standard library
 * (no Apache Commons FileUpload, Spring Web, or Jersey dependencies).
 */
public final class MultipartBodyPublisher {

    private static final String CRLF = "\r\n";
    private static final byte[] CRLF_BYTES = CRLF.getBytes(StandardCharsets.UTF_8);

    private String boundary;
    private final List<Part> parts = new ArrayList<>();

    public MultipartBodyPublisher() {
        this("---MSPBoundary" + UUID.randomUUID().toString().replace("-", ""));
    }

    public MultipartBodyPublisher(String boundary) {
        this.boundary = Objects.requireNonNull(boundary, "boundary must not be null");
    }

    /**
     * Creates a new builder instance with an auto-generated random boundary.
     */
    public static MultipartBodyPublisher newBuilder() {
        return new MultipartBodyPublisher();
    }

    /**
     * Sets a custom boundary string.
     */
    public MultipartBodyPublisher boundary(String boundary) {
        this.boundary = Objects.requireNonNull(boundary, "boundary must not be null");
        return this;
    }

    /**
     * Adds a plain text form field.
     *
     * @param name  form field name
     * @param value field value
     */
    public MultipartBodyPublisher addPart(String name, String value) {
        Objects.requireNonNull(name, "name must not be null");
        String val = (value != null) ? value : "";
        byte[] data = val.getBytes(StandardCharsets.UTF_8);

        String header = "--" + boundary + CRLF +
                "Content-Disposition: form-data; name=\"" + escapeQuotes(name) + "\"" + CRLF +
                "Content-Type: text/plain; charset=UTF-8" + CRLF +
                CRLF;

        parts.add(new Part(header.getBytes(StandardCharsets.UTF_8), data));
        return this;
    }

    /**
     * Adds a binary file part with specified filename and content type.
     *
     * @param name        form field name
     * @param filename    original file name
     * @param contentType MIME content type (e.g. {@code image/png}, {@code application/pdf})
     * @param data        binary file data
     */
    public MultipartBodyPublisher addPart(String name, String filename, String contentType, byte[] data) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
        String mime = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
        byte[] payload = (data != null) ? data.clone() : new byte[0];

        String header = "--" + boundary + CRLF +
                "Content-Disposition: form-data; name=\"" + escapeQuotes(name) + "\"; filename=\"" + escapeQuotes(filename) + "\"" + CRLF +
                "Content-Type: " + mime + CRLF +
                CRLF;

        parts.add(new Part(header.getBytes(StandardCharsets.UTF_8), payload));
        return this;
    }

    /**
     * Adds a file part by reading from an {@link InputStream}.
     *
     * @param name        form field name
     * @param filename    original file name
     * @param contentType MIME content type
     * @param is          input stream containing file data
     */
    public MultipartBodyPublisher addPart(String name, String filename, String contentType, InputStream is) {
        Objects.requireNonNull(is, "InputStream must not be null");
        try {
            byte[] data = is.readAllBytes();
            return addPart(name, filename, contentType, data);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read InputStream for part: " + name, e);
        }
    }

    /**
     * Adds a file part from a local {@link Path}.
     */
    public MultipartBodyPublisher addPart(String name, Path path) {
        Objects.requireNonNull(path, "path must not be null");
        String filename = path.getFileName().toString();
        try {
            String mime = Files.probeContentType(path);
            byte[] data = Files.readAllBytes(path);
            return addPart(name, filename, mime, data);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file from path: " + path, e);
        }
    }

    /**
     * Returns the multipart boundary string.
     */
    public String getBoundary() {
        return boundary;
    }

    /**
     * Returns the complete HTTP {@code Content-Type} header value including boundary.
     * <p>
     * Example: {@code multipart/form-data; boundary=---MSPBoundary123}
     */
    public String getContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    /**
     * Returns total byte count of the multipart body payload.
     */
    public long contentLength() {
        return toByteArray().length;
    }

    /**
     * Assembles all parts into a complete multipart body byte array.
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            for (Part part : parts) {
                baos.write(part.headerBytes);
                baos.write(part.bodyBytes);
                baos.write(CRLF_BYTES);
            }
            // Final closing boundary
            String closing = "--" + boundary + "--" + CRLF;
            baos.write(closing.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize multipart body", e);
        }
        return baos.toByteArray();
    }

    /**
     * Builds and returns a standard Java {@link HttpRequest.BodyPublisher} ready for use with {@link java.net.http.HttpClient}.
     */
    public HttpRequest.BodyPublisher build() {
        byte[] body = toByteArray();
        return HttpRequest.BodyPublishers.ofByteArray(body);
    }

    private static String escapeQuotes(String s) {
        return s.replace("\"", "\\\"");
    }

    private record Part(byte[] headerBytes, byte[] bodyBytes) {}
}
