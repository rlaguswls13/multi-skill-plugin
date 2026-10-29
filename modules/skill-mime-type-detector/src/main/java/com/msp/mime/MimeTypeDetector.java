package com.msp.mime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fast, lightweight MIME type detector based on binary magic numbers and file extensions.
 * Zero external dependencies, pure Java 21.
 */
public final class MimeTypeDetector {

    public static final String OCTET_STREAM = "application/octet-stream";
    public static final String PLAIN_TEXT = "text/plain";

    private static final MimeTypeDetector INSTANCE = new MimeTypeDetector();

    private final Map<String, String> extensionToMime = new HashMap<>(256);

    public MimeTypeDetector() {
        registerCommonExtensions();
    }

    public static MimeTypeDetector getInstance() {
        return INSTANCE;
    }

    /**
     * Detects MIME type for a file path. Reads first bytes for magic number sniffing,
     * falling back to extension matching.
     */
    public static String detect(Path path) throws IOException {
        return INSTANCE.detectPath(path);
    }

    /**
     * Detects MIME type from the initial header bytes (typically first 32-64 bytes).
     */
    public static String detect(byte[] headerBytes) {
        return INSTANCE.detectHeader(headerBytes);
    }

    /**
     * Detects MIME type using both header bytes and filename/extension for context.
     */
    public static String detect(byte[] headerBytes, String filenameOrExtension) {
        return INSTANCE.detectHeaderAndExtension(headerBytes, filenameOrExtension);
    }

    /**
     * Detects MIME type solely based on file name or file extension.
     */
    public static String detectByExtension(String filenameOrExtension) {
        return INSTANCE.detectByExt(filenameOrExtension);
    }

    public String detectPath(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path) || Files.isDirectory(path)) {
            return detectByExt(path.getFileName() != null ? path.getFileName().toString() : "");
        }

        byte[] header = new byte[64];
        int bytesRead;
        try (InputStream in = Files.newInputStream(path)) {
            bytesRead = in.read(header);
        }

        String filename = path.getFileName() != null ? path.getFileName().toString() : "";
        if (bytesRead <= 0) {
            return detectByExt(filename);
        }

        byte[] actualHeader = bytesRead == header.length ? header : Arrays.copyOf(header, bytesRead);
        return detectHeaderAndExtension(actualHeader, filename);
    }

    public String detectHeader(byte[] headerBytes) {
        if (headerBytes == null || headerBytes.length == 0) {
            return OCTET_STREAM;
        }

        String magicMime = sniffMagic(headerBytes);
        return magicMime != null ? magicMime : OCTET_STREAM;
    }

    public String detectHeaderAndExtension(byte[] headerBytes, String filenameOrExtension) {
        String extMime = detectByExt(filenameOrExtension);

        if (headerBytes == null || headerBytes.length == 0) {
            return extMime;
        }

        String magicMime = sniffMagic(headerBytes);
        if (magicMime != null) {
            // For zip container formats (docx, xlsx, pptx, jar, etc.), refine if extension is known
            if ("application/zip".equals(magicMime)) {
                if (extMime != null && !OCTET_STREAM.equals(extMime) && isZipDerived(extMime)) {
                    return extMime;
                }
            }
            return magicMime;
        }

        // If magic sniff couldn't identify, fallback to extension if valid
        if (extMime != null && !OCTET_STREAM.equals(extMime)) {
            return extMime;
        }

        // If plain text content without extension
        if (isAsciiText(headerBytes)) {
            return PLAIN_TEXT;
        }

        return OCTET_STREAM;
    }

    public String detectByExt(String filenameOrExtension) {
        if (filenameOrExtension == null || filenameOrExtension.isBlank()) {
            return OCTET_STREAM;
        }

        String ext = extractExtension(filenameOrExtension).toLowerCase(Locale.ROOT);
        String mime = extensionToMime.get(ext);
        return mime != null ? mime : OCTET_STREAM;
    }

    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public static boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public static boolean isAudio(String mimeType) {
        return mimeType != null && mimeType.startsWith("audio/");
    }

    public static boolean isText(String mimeType) {
        return mimeType != null && (mimeType.startsWith("text/")
                || "application/json".equals(mimeType)
                || "application/xml".equals(mimeType)
                || "application/javascript".equals(mimeType));
    }

    private static String extractExtension(String filename) {
        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1);
        }
        return name;
    }

    private static boolean isZipDerived(String mime) {
        return mime.contains("openxmlformats")
                || mime.contains("vnd.ms-")
                || mime.contains("java-archive")
                || mime.contains("android.package-archive");
    }

    private String sniffMagic(byte[] b) {
        int len = b.length;

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (len >= 8 && matches(b, 0, (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }

        // JPEG: FF D8 FF
        if (len >= 3 && matches(b, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
            return "image/jpeg";
        }

        // GIF: "GIF87a" or "GIF89a"
        if (len >= 6 && matches(b, 0, 'G', 'I', 'F', '8', null, 'a')) {
            return "image/gif";
        }

        // WebP: RIFF....WEBP
        if (len >= 12 && matches(b, 0, 'R', 'I', 'F', 'F') && matches(b, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }

        // BMP: BM (42 4D)
        if (len >= 2 && matches(b, 0, 'B', 'M')) {
            return "image/bmp";
        }

        // TIFF: II*. or MM.*
        if (len >= 4 && (matches(b, 0, 'I', 'I', 42, 0) || matches(b, 0, 'M', 'M', 0, 42))) {
            return "image/tiff";
        }

        // ICO: 00 00 01 00
        if (len >= 4 && matches(b, 0, 0, 0, 1, 0)) {
            return "image/x-icon";
        }

        // PDF: %PDF- (25 50 44 46 2D)
        if (len >= 5 && matches(b, 0, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }

        // ZIP: PK\x03\x04 or PK\x05\x06 or PK\x07\x08
        if (len >= 4 && matches(b, 0, 'P', 'K') &&
                ((b[2] == 3 && b[3] == 4) || (b[2] == 5 && b[3] == 6) || (b[2] == 7 && b[3] == 8))) {
            return "application/zip";
        }

        // GZIP: 1F 8B
        if (len >= 2 && matches(b, 0, 0x1F, 0x8B)) {
            return "application/gzip";
        }

        // 7-Zip: 37 7A BC AF 27 1C
        if (len >= 6 && matches(b, 0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) {
            return "application/x-7z-compressed";
        }

        // BZIP2: BZh
        if (len >= 3 && matches(b, 0, 'B', 'Z', 'h')) {
            return "application/x-bzip2";
        }

        // RAR: Rar! (52 61 72 21)
        if (len >= 4 && matches(b, 0, 'R', 'a', 'r', '!')) {
            return "application/vnd.rar";
        }

        // WebAssembly: \0asm (00 61 73 6D)
        if (len >= 4 && matches(b, 0, 0x00, 'a', 's', 'm')) {
            return "application/wasm";
        }

        // FLAC: fLaC
        if (len >= 4 && matches(b, 0, 'f', 'L', 'a', 'C')) {
            return "audio/flac";
        }

        // Ogg: OggS
        if (len >= 4 && matches(b, 0, 'O', 'g', 'g', 'S')) {
            return "audio/ogg";
        }

        // WAV: RIFF....WAVE
        if (len >= 12 && matches(b, 0, 'R', 'I', 'F', 'F') && matches(b, 8, 'W', 'A', 'V', 'E')) {
            return "audio/wav";
        }

        // MP4: ....ftyp
        if (len >= 8 && matches(b, 4, 'f', 't', 'y', 'p')) {
            return "video/mp4";
        }

        // MP3: ID3 or sync frame FF FB / FF F3 / FF F2
        if (len >= 3 && matches(b, 0, 'I', 'D', '3')) {
            return "audio/mpeg";
        }
        if (len >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0) {
            return "audio/mpeg";
        }

        // EBML (Matroska / WebM): 1A 45 DF A3
        if (len >= 4 && matches(b, 0, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "video/webm";
        }

        // Text-based formats: SVG, XML, HTML
        String textSnippet = new String(b, 0, Math.min(len, 64), StandardCharsets.UTF_8).trim();
        String lowerSnippet = textSnippet.toLowerCase(Locale.ROOT);

        if (lowerSnippet.startsWith("<?xml")) {
            if (lowerSnippet.contains("<svg")) {
                return "image/svg+xml";
            }
            return "application/xml";
        }
        if (lowerSnippet.startsWith("<svg") || lowerSnippet.contains("<svg")) {
            return "image/svg+xml";
        }
        if (lowerSnippet.startsWith("<!doctype html") || lowerSnippet.startsWith("<html")) {
            return "text/html";
        }

        return null;
    }

    private static boolean matches(byte[] src, int offset, Object... pattern) {
        if (src.length < offset + pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            Object expected = pattern[i];
            if (expected == null) {
                continue; // wildcard
            }
            int actual = src[offset + i] & 0xFF;
            int exp;
            if (expected instanceof Character c) {
                exp = c;
            } else if (expected instanceof Number n) {
                exp = n.intValue() & 0xFF;
            } else {
                return false;
            }
            if (actual != exp) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiText(byte[] b) {
        int checkLen = Math.min(b.length, 128);
        for (int i = 0; i < checkLen; i++) {
            int val = b[i] & 0xFF;
            // 0x00 is binary null byte
            if (val == 0) {
                return false;
            }
            // Allow printable ASCII and common whitespace (\t, \n, \r)
            if (val < 0x09 || (val > 0x0D && val < 0x20)) {
                return false;
            }
        }
        return true;
    }

    private void registerCommonExtensions() {
        // Images (15+)
        put("png", "image/png");
        put("jpg", "image/jpeg");
        put("jpeg", "image/jpeg");
        put("gif", "image/gif");
        put("webp", "image/webp");
        put("svg", "image/svg+xml");
        put("svgz", "image/svg+xml");
        put("bmp", "image/bmp");
        put("ico", "image/x-icon");
        put("tiff", "image/tiff");
        put("tif", "image/tiff");
        put("avif", "image/avif");
        put("heic", "image/heic");
        put("heif", "image/heif");
        put("psd", "image/vnd.adobe.photoshop");

        // Documents & Office (15+)
        put("pdf", "application/pdf");
        put("doc", "application/msword");
        put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        put("xls", "application/vnd.ms-excel");
        put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        put("ppt", "application/vnd.ms-powerpoint");
        put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        put("odt", "application/vnd.oasis.opendocument.text");
        put("ods", "application/vnd.oasis.opendocument.spreadsheet");
        put("odp", "application/vnd.oasis.opendocument.presentation");
        put("rtf", "application/rtf");
        put("epub", "application/epub+zip");
        put("csv", "text/csv");
        put("tsv", "text/tab-separated-values");
        put("txt", "text/plain");
        put("md", "text/markdown");
        put("log", "text/plain");

        // Archives (15+)
        put("zip", "application/zip");
        put("tar", "application/x-tar");
        put("gz", "application/gzip");
        put("tgz", "application/gzip");
        put("bz2", "application/x-bzip2");
        put("7z", "application/x-7z-compressed");
        put("rar", "application/vnd.rar");
        put("xz", "application/x-xz");
        put("jar", "application/java-archive");
        put("war", "application/java-archive");
        put("ear", "application/java-archive");
        put("apk", "application/vnd.android.package-archive");
        put("iso", "application/x-iso9660-image");
        put("dmg", "application/x-apple-diskimage");
        put("zst", "application/zstd");

        // Audio (10+)
        put("mp3", "audio/mpeg");
        put("wav", "audio/wav");
        put("ogg", "audio/ogg");
        put("oga", "audio/ogg");
        put("flac", "audio/flac");
        put("aac", "audio/aac");
        put("m4a", "audio/mp4");
        put("wma", "audio/x-ms-wma");
        put("mid", "audio/midi");
        put("midi", "audio/midi");
        put("weba", "audio/webm");

        // Video (12+)
        put("mp4", "video/mp4");
        put("mkv", "video/x-matroska");
        put("webm", "video/webm");
        put("avi", "video/x-msvideo");
        put("mov", "video/quicktime");
        put("wmv", "video/x-ms-wmv");
        put("flv", "video/x-flv");
        put("m4v", "video/x-m4v");
        put("ts", "video/mp2t");
        put("3gp", "video/3gpp");
        put("ogv", "video/ogg");

        // Web, Code & Structured Data (30+)
        put("html", "text/html");
        put("htm", "text/html");
        put("css", "text/css");
        put("scss", "text/x-scss");
        put("sass", "text/x-sass");
        put("less", "text/x-less");
        put("js", "application/javascript");
        put("mjs", "application/javascript");
        put("cjs", "application/javascript");
        put("ts", "application/typescript");
        put("tsx", "application/typescript");
        put("jsx", "application/javascript");
        put("json", "application/json");
        put("jsonld", "application/ld+json");
        put("xml", "application/xml");
        put("xsl", "application/xml");
        put("yaml", "application/yaml");
        put("yml", "application/yaml");
        put("sql", "application/sql");
        put("graphql", "application/graphql");
        put("sh", "application/x-sh");
        put("bat", "application/x-bat");
        put("ps1", "text/plain");
        put("py", "text/x-python");
        put("java", "text/x-java-source");
        put("kt", "text/x-kotlin");
        put("go", "text/x-go");
        put("rs", "text/x-rust");
        put("c", "text/x-c");
        put("cpp", "text/x-c++");
        put("h", "text/x-c");
        put("wasm", "application/wasm");
        put("map", "application/json");

        // Fonts (6+)
        put("ttf", "font/ttf");
        put("otf", "font/otf");
        put("woff", "font/woff");
        put("woff2", "font/woff2");
        put("eot", "application/vnd.ms-fontobject");
    }

    private void put(String extension, String mimeType) {
        extensionToMime.put(extension, mimeType);
    }
}
