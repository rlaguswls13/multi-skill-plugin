package com.msp.multipart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MultipartStreamParserTest {

    @Test
    @DisplayName("Parse standard form-data with text field and file")
    void testParseMultipart() throws Exception {
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"username\"\r\n\r\n"
                + "tester123\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"upload\"; filename=\"memo.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + "Hello MultiPart World!\r\n"
                + "--" + boundary + "--\r\n";

        InputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        try (MultipartResult result = MultipartStreamParser.parse(in, boundary, 1024)) {
            assertEquals(2, result.parts().size());

            // Field 1
            MultipartPart userPart = result.getPart("username").orElseThrow();
            assertEquals("username", userPart.name());
            assertNull(userPart.filename());
            assertFalse(userPart.isFile());
            assertEquals("tester123", userPart.asString());
            assertTrue(userPart.isInMemory());

            // Field 2 (File)
            MultipartPart filePart = result.getPart("upload").orElseThrow();
            assertEquals("upload", filePart.name());
            assertEquals("memo.txt", filePart.filename());
            assertTrue(filePart.isFile());
            assertEquals("text/plain", filePart.contentType());
            assertEquals("Hello MultiPart World!", filePart.asString());
        }
    }

    @Test
    @DisplayName("Disk swap on memory threshold exceeded")
    void testDiskSwapThreshold() throws Exception {
        String boundary = "===Boundary===";
        String bigPayload = "A".repeat(200);
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"bigfile\"; filename=\"big.dat\"\r\n\r\n"
                + bigPayload + "\r\n"
                + "--" + boundary + "--\r\n";

        InputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        Path tempPath = null;
        try (MultipartResult result = MultipartStreamParser.parse(in, boundary, 50)) { // 50 bytes threshold
            MultipartPart part = result.getPart("bigfile").orElseThrow();
            assertFalse(part.isInMemory());
            assertNotNull(part.tempFilePath());
            assertTrue(Files.exists(part.tempFilePath()));
            tempPath = part.tempFilePath();
            assertEquals(bigPayload, part.asString());
        }

        // Temp file should be deleted on close
        assertNotNull(tempPath);
        assertFalse(Files.exists(tempPath));
    }
}
