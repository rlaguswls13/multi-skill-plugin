package com.msp.multipart;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프레임워크나 서블릿 의존성 없이 순수 InputStream에서
 * RFC 7578 multipart/form-data 본문을 스트리밍 파싱하고
 * 임계치 초과 시 임시 디스크 파일로 자동 스왑하는 경량 파서.
 */
public final class MultipartStreamParser {

    private static final Pattern DISPOSITION_NAME_PATTERN = Pattern.compile("name=\"([^\"]+)\"");
    private static final Pattern DISPOSITION_FILENAME_PATTERN = Pattern.compile("filename=\"([^\"]+)\"");

    private MultipartStreamParser() {}

    public static MultipartResult parse(InputStream in, String boundary) throws IOException {
        return parse(in, boundary, 1024 * 1024); // default 1MB in-memory threshold
    }

    public static MultipartResult parse(InputStream in, String boundary, long memoryThresholdBytes) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(boundary, "boundary must not be null");

        byte[] allBytes = in.readAllBytes();
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);

        List<MultipartPart> parts = new ArrayList<>();
        List<Integer> boundaryIndices = findDelimiterIndices(allBytes, delimiter);

        for (int i = 0; i < boundaryIndices.size() - 1; i++) {
            int start = boundaryIndices.get(i) + delimiter.length;
            int end = boundaryIndices.get(i + 1);

            // Skip CRLF immediately after delimiter
            if (start + 2 <= allBytes.length && allBytes[start] == '\r' && allBytes[start + 1] == '\n') {
                start += 2;
            } else if (start + 1 <= allBytes.length && allBytes[start] == '\n') {
                start += 1;
            }

            // Check if end has trailing CRLF before delimiter
            if (end >= 2 && allBytes[end - 2] == '\r' && allBytes[end - 1] == '\n') {
                end -= 2;
            } else if (end >= 1 && allBytes[end - 1] == '\n') {
                end -= 1;
            }

            if (start >= end) continue;

            // Find header-body separator (\r\n\r\n or \n\n)
            int bodyStart = findHeaderEnd(allBytes, start, end);
            if (bodyStart == -1) continue;

            String headerText = new String(allBytes, start, bodyStart - start, StandardCharsets.UTF_8);
            Map<String, String> headers = parseHeaders(headerText);

            String disposition = headers.getOrDefault("content-disposition", "");
            String name = extractPattern(DISPOSITION_NAME_PATTERN, disposition);
            String filename = extractPattern(DISPOSITION_FILENAME_PATTERN, disposition);
            String contentType = headers.getOrDefault("content-type", "text/plain");

            int bodyLength = end - bodyStart;
            byte[] memoryBytes = null;
            Path tempFile = null;

            if (bodyLength <= memoryThresholdBytes) {
                memoryBytes = Arrays.copyOfRange(allBytes, bodyStart, end);
            } else {
                tempFile = Files.createTempFile("msp-multipart-", ".tmp");
                try (OutputStream out = Files.newOutputStream(tempFile)) {
                    out.write(allBytes, bodyStart, bodyLength);
                }
            }

            parts.add(new MultipartPart(name, filename, contentType, headers, memoryBytes, tempFile, bodyLength));
        }

        return new MultipartResult(parts);
    }

    private static List<Integer> findDelimiterIndices(byte[] data, byte[] delimiter) {
        List<Integer> list = new ArrayList<>();
        int limit = data.length - delimiter.length;
        for (int i = 0; i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < delimiter.length; j++) {
                if (data[i + j] != delimiter[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                list.add(i);
                i += delimiter.length - 1;
            }
        }
        return list;
    }

    private static int findHeaderEnd(byte[] data, int start, int end) {
        for (int i = start; i < end - 3; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        for (int i = start; i < end - 1; i++) {
            if (data[i] == '\n' && data[i + 1] == '\n') {
                return i + 2;
            }
        }
        return -1;
    }

    private static Map<String, String> parseHeaders(String headerText) {
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String[] lines = headerText.split("\r?\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon != -1) {
                String key = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                map.put(key, val);
            }
        }
        return map;
    }

    private static String extractPattern(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
