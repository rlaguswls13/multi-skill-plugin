package com.msp.multiupload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MultipartBodyPublisherTest {

    @Test
    @DisplayName("Build multipart payload with text parts and verify RFC structure")
    void testTextParts() {
        MultipartBodyPublisher publisher = MultipartBodyPublisher.newBuilder()
                .boundary("TestBoundary123")
                .addPart("username", "developer42")
                .addPart("role", "ADMIN");

        assertEquals("TestBoundary123", publisher.getBoundary());
        assertEquals("multipart/form-data; boundary=TestBoundary123", publisher.getContentType());

        String body = new String(publisher.toByteArray(), StandardCharsets.UTF_8);

        assertTrue(body.contains("--TestBoundary123\r\n"));
        assertTrue(body.contains("Content-Disposition: form-data; name=\"username\"\r\n"));
        assertTrue(body.contains("developer42\r\n"));
        assertTrue(body.contains("Content-Disposition: form-data; name=\"role\"\r\n"));
        assertTrue(body.contains("ADMIN\r\n"));
        assertTrue(body.endsWith("--TestBoundary123--\r\n"));
    }

    @Test
    @DisplayName("Build multipart payload with binary byte array and InputStream")
    void testBinaryAndInputStreamParts() {
        byte[] sampleImage = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        InputStream stream = new ByteArrayInputStream("Hello Stream Content".getBytes(StandardCharsets.UTF_8));

        MultipartBodyPublisher publisher = MultipartBodyPublisher.newBuilder()
                .boundary("BinBoundaryXYZ")
                .addPart("avatar", "profile.png", "image/png", sampleImage)
                .addPart("doc", "readme.txt", "text/plain", stream);

        byte[] totalBytes = publisher.toByteArray();
        assertEquals(publisher.contentLength(), totalBytes.length);

        String textRepresentation = new String(totalBytes, StandardCharsets.ISO_8859_1);
        assertTrue(textRepresentation.contains("Content-Disposition: form-data; name=\"avatar\"; filename=\"profile.png\"\r\n"));
        assertTrue(textRepresentation.contains("Content-Type: image/png\r\n"));
        assertTrue(textRepresentation.contains("Content-Disposition: form-data; name=\"doc\"; filename=\"readme.txt\"\r\n"));
        assertTrue(textRepresentation.contains("Hello Stream Content\r\n"));
    }

    @Test
    @DisplayName("Build multipart payload with Path file upload")
    void testPathUpload(@TempDir Path tempDir) throws Exception {
        Path tempFile = tempDir.resolve("sample.json");
        Files.writeString(tempFile, "{\"status\":\"ok\"}");

        MultipartBodyPublisher publisher = MultipartBodyPublisher.newBuilder()
                .boundary("FileBoundary999")
                .addPart("attachment", tempFile);

        String body = new String(publisher.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.contains("filename=\"sample.json\""));
        assertTrue(body.contains("{\"status\":\"ok\"}"));
    }

    @Test
    @DisplayName("Standard HttpRequest.BodyPublisher compatibility")
    void testHttpRequestBodyPublisher() {
        MultipartBodyPublisher publisher = MultipartBodyPublisher.newBuilder()
                .boundary("HttpReqBoundary")
                .addPart("field", "value");

        HttpRequest.BodyPublisher bodyPublisher = publisher.build();
        assertNotNull(bodyPublisher);
        assertEquals(publisher.contentLength(), bodyPublisher.contentLength());
    }

    @Test
    @DisplayName("Escape double quotes in part names and filenames")
    void testEscapingQuotes() {
        MultipartBodyPublisher publisher = MultipartBodyPublisher.newBuilder()
                .boundary("EscapeBoundary")
                .addPart("my\"field", "quote\"file.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));

        String body = new String(publisher.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.contains("name=\"my\\\"field\""));
        assertTrue(body.contains("filename=\"quote\\\"file.txt\""));
    }
}
