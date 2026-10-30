package com.msp.multipart;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 파싱된 멀티파트 단일 파트(Form 필드 또는 파일).
 */
public final class MultipartPart {

    private final String name;
    private final String filename;
    private final String contentType;
    private final Map<String, String> headers;
    private final byte[] memoryBytes;
    private final Path tempFilePath;
    private final long size;

    public MultipartPart(String name, String filename, String contentType, Map<String, String> headers, byte[] memoryBytes, Path tempFilePath, long size) {
        this.name = name;
        this.filename = filename;
        this.contentType = contentType;
        this.headers = headers;
        this.memoryBytes = memoryBytes;
        this.tempFilePath = tempFilePath;
        this.size = size;
    }

    public String name() {
        return name;
    }

    public String filename() {
        return filename;
    }

    public boolean isFile() {
        return filename != null;
    }

    public String contentType() {
        return contentType;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public boolean isInMemory() {
        return memoryBytes != null;
    }

    public long size() {
        return size;
    }

    public Path tempFilePath() {
        return tempFilePath;
    }

    public byte[] bytes() throws IOException {
        if (memoryBytes != null) {
            return memoryBytes;
        }
        if (tempFilePath != null && Files.exists(tempFilePath)) {
            return Files.readAllBytes(tempFilePath);
        }
        return new byte[0];
    }

    public InputStream openStream() throws IOException {
        if (memoryBytes != null) {
            return new ByteArrayInputStream(memoryBytes);
        }
        if (tempFilePath != null && Files.exists(tempFilePath)) {
            return new FileInputStream(tempFilePath.toFile());
        }
        return InputStream.nullInputStream();
    }

    public String asString() throws IOException {
        return asString(StandardCharsets.UTF_8);
    }

    public String asString(Charset charset) throws IOException {
        return new String(bytes(), charset);
    }
}
