package com.msp.mime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MimeTypeDetectorTest {

    @Test
    @DisplayName("Should detect MIME type by magic bytes for binary files")
    void shouldDetectByMagicBytes() {
        // PNG magic: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertEquals("image/png", MimeTypeDetector.detect(pngHeader));

        // JPEG magic: FF D8 FF
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        assertEquals("image/jpeg", MimeTypeDetector.detect(jpegHeader));

        // PDF magic: %PDF-
        byte[] pdfHeader = "%PDF-1.7 header content here".getBytes(StandardCharsets.US_ASCII);
        assertEquals("application/pdf", MimeTypeDetector.detect(pdfHeader));

        // ZIP magic: PK\x03\x04
        byte[] zipHeader = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0, 0, 0};
        assertEquals("application/zip", MimeTypeDetector.detect(zipHeader));

        // GZIP magic: 1F 8B
        byte[] gzipHeader = new byte[]{0x1F, (byte) 0x8B, 0x08, 0};
        assertEquals("application/gzip", MimeTypeDetector.detect(gzipHeader));

        // GIF magic: GIF89a
        byte[] gifHeader = "GIF89a...".getBytes(StandardCharsets.US_ASCII);
        assertEquals("image/gif", MimeTypeDetector.detect(gifHeader));

        // 7-Zip magic: 37 7A BC AF 27 1C
        byte[] sevenZipHeader = new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
        assertEquals("application/x-7z-compressed", MimeTypeDetector.detect(sevenZipHeader));
    }

    @Test
    @DisplayName("Should detect audio and video magic bytes")
    void shouldDetectMediaMagicBytes() {
        // MP3 ID3 header
        byte[] id3Header = new byte[]{'I', 'D', '3', 0x04, 0, 0};
        assertEquals("audio/mpeg", MimeTypeDetector.detect(id3Header));

        // MP4 ftyp header (offset 4)
        byte[] mp4Header = new byte[]{0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        assertEquals("video/mp4", MimeTypeDetector.detect(mp4Header));

        // WAV header: RIFF....WAVE
        byte[] wavHeader = new byte[]{'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E'};
        assertEquals("audio/wav", MimeTypeDetector.detect(wavHeader));
    }

    @Test
    @DisplayName("Should detect text-based XML and SVG formats")
    void shouldDetectXmlAndSvg() {
        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertEquals("image/svg+xml", MimeTypeDetector.detect(svgBytes));

        byte[] xmlWithSvg = "<?xml version=\"1.0\"?>\n<svg viewBox=\"0 0 10 10\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertEquals("image/svg+xml", MimeTypeDetector.detect(xmlWithSvg));

        byte[] regularXml = "<?xml version=\"1.0\"?>\n<root><node>1</node></root>".getBytes(StandardCharsets.UTF_8);
        assertEquals("application/xml", MimeTypeDetector.detect(regularXml));

        byte[] htmlBytes = "<!DOCTYPE html><html><body>Test</body></html>".getBytes(StandardCharsets.UTF_8);
        assertEquals("text/html", MimeTypeDetector.detect(htmlBytes));
    }

    @Test
    @DisplayName("Should detect MIME type by file extension")
    void shouldDetectByExtension() {
        assertEquals("application/json", MimeTypeDetector.detectByExtension("config.json"));
        assertEquals("text/css", MimeTypeDetector.detectByExtension("styles.css"));
        assertEquals("application/javascript", MimeTypeDetector.detectByExtension("app.js"));
        assertEquals("text/markdown", MimeTypeDetector.detectByExtension("README.md"));
        assertEquals("text/csv", MimeTypeDetector.detectByExtension("data.csv"));
        assertEquals("application/yaml", MimeTypeDetector.detectByExtension("docker-compose.yml"));
        assertEquals("application/wasm", MimeTypeDetector.detectByExtension("module.wasm"));
        assertEquals("font/woff2", MimeTypeDetector.detectByExtension("font.woff2"));
    }

    @Test
    @DisplayName("Should refine ZIP container format using file extension")
    void shouldRefineZipFormatWithExtension() {
        byte[] zipHeader = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0, 0, 0};

        // Raw zip with generic name
        assertEquals("application/zip", MimeTypeDetector.detect(zipHeader, "archive.zip"));

        // Word DOCX
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                MimeTypeDetector.detect(zipHeader, "document.docx"));

        // Excel XLSX
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                MimeTypeDetector.detect(zipHeader, "sheet.xlsx"));

        // JAR file
        assertEquals("application/java-archive",
                MimeTypeDetector.detect(zipHeader, "plugin.jar"));
    }

    @Test
    @DisplayName("Should fallback to application/octet-stream for unknown binary")
    void shouldFallbackToOctetStream() {
        byte[] randomBinary = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
        assertEquals("application/octet-stream", MimeTypeDetector.detect(randomBinary));
        assertEquals("application/octet-stream", MimeTypeDetector.detectByExtension("file.unknownext123"));
        assertEquals("application/octet-stream", MimeTypeDetector.detect((byte[]) null));
    }

    @Test
    @DisplayName("Should detect MIME from actual file on disk")
    void shouldDetectFromPath(@TempDir Path tempDir) throws IOException {
        Path pngFile = tempDir.resolve("sample.png");
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        Files.write(pngFile, pngHeader);

        assertEquals("image/png", MimeTypeDetector.detect(pngFile));

        Path jsonFile = tempDir.resolve("data.json");
        Files.writeString(jsonFile, "{\"name\": \"test\"}");
        assertEquals("application/json", MimeTypeDetector.detect(jsonFile));
    }

    @Test
    @DisplayName("Should verify type check helper methods")
    void shouldVerifyTypeHelpers() {
        assertTrue(MimeTypeDetector.isImage("image/png"));
        assertTrue(MimeTypeDetector.isImage("image/jpeg"));
        assertFalse(MimeTypeDetector.isImage("text/html"));

        assertTrue(MimeTypeDetector.isVideo("video/mp4"));
        assertFalse(MimeTypeDetector.isVideo("audio/mpeg"));

        assertTrue(MimeTypeDetector.isAudio("audio/mpeg"));
        assertTrue(MimeTypeDetector.isAudio("audio/wav"));

        assertTrue(MimeTypeDetector.isText("text/plain"));
        assertTrue(MimeTypeDetector.isText("application/json"));
        assertTrue(MimeTypeDetector.isText("application/xml"));
    }
}
