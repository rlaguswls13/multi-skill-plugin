package com.msp.tablecopier.api;

import com.msp.tablecopier.parser.TabularSourceFormat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 엑셀(.xlsx/.csv) 또는 텍스트(.txt) 입력 소스를 추상화한 API 요청 입력 객체.
 */
public record SourceDataInput(
        Path filePath,
        byte[] fileBytes,
        String fileNameHint,
        String rawText,
        TabularSourceFormat format,
        String customDelimiter,
        boolean firstRowIsHeader,
        Charset charset
) {
    public SourceDataInput {
        if (format == null) {
            format = TabularSourceFormat.AUTO;
        }
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
    }

    public static SourceDataInput fromFile(Path filePath) {
        return new SourceDataInput(filePath, null, filePath != null ? filePath.getFileName().toString() : null,
                null, TabularSourceFormat.AUTO, null, true, StandardCharsets.UTF_8);
    }

    public static SourceDataInput fromFile(Path filePath, String customDelimiter, boolean firstRowIsHeader) {
        return new SourceDataInput(filePath, null, filePath != null ? filePath.getFileName().toString() : null,
                null, TabularSourceFormat.AUTO, customDelimiter, firstRowIsHeader, StandardCharsets.UTF_8);
    }

    public static SourceDataInput fromBytes(byte[] fileBytes, String fileNameHint, boolean firstRowIsHeader) {
        return new SourceDataInput(null, fileBytes, fileNameHint, null,
                TabularSourceFormat.AUTO, null, firstRowIsHeader, StandardCharsets.UTF_8);
    }

    public static SourceDataInput fromText(String rawText) {
        return new SourceDataInput(null, null, null, rawText,
                TabularSourceFormat.AUTO, null, true, StandardCharsets.UTF_8);
    }

    public static SourceDataInput fromText(String rawText, String customDelimiter, boolean firstRowIsHeader) {
        return new SourceDataInput(null, null, null, rawText,
                TabularSourceFormat.AUTO, customDelimiter, firstRowIsHeader, StandardCharsets.UTF_8);
    }
}
