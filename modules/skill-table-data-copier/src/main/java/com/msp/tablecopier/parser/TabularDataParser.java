package com.msp.tablecopier.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 외부 라이브러리 의존성 없이 Excel(.xlsx) 및 텍스트(.txt, .csv, .tsv) 데이터를
 * 행/열 구조({@link ParsedTableData})로 파싱하는 경량 파서.
 */
public class TabularDataParser {

    /**
     * 파일 경로로부터 테이블 데이터를 파싱합니다.
     *
     * @param filePath         엑셀(.xlsx) 또는 텍스트(.txt, .csv, .tsv) 파일 경로
     * @param format           포맷 (AUTO 지정 시 확장자 및 내용으로 자동 판별)
     * @param customDelimiter  TXT_CUSTOM 포맷일 때 사용할 구분자 (예: "|", ";", "\t")
     * @param firstRowIsHeader 첫 번째 행을 컬럼 헤더로 사용할지 여부
     * @param charset          텍스트 파일 인코딩 (null일 경우 UTF-8 기본 적용)
     */
    public ParsedTableData parseFile(
            Path filePath,
            TabularSourceFormat format,
            String customDelimiter,
            boolean firstRowIsHeader,
            Charset charset
    ) throws IOException {
        if (filePath == null || !Files.exists(filePath)) {
            throw new IllegalArgumentException("입력 파일이 존재하지 않습니다: " + filePath);
        }
        TabularSourceFormat resolvedFormat = resolveFormat(filePath.getFileName().toString(), format, customDelimiter);
        if (resolvedFormat == TabularSourceFormat.EXCEL_XLSX) {
            try (InputStream in = Files.newInputStream(filePath)) {
                return parseXlsxStream(in, firstRowIsHeader);
            }
        }
        Charset effectiveCharset = charset != null ? charset : StandardCharsets.UTF_8;
        String textContent = Files.readString(filePath, effectiveCharset);
        return parseText(textContent, resolvedFormat, customDelimiter, firstRowIsHeader);
    }

    /**
     * 바이트 배열(업로드된 엑셀 파일 또는 텍스트 스트림)로부터 테이블 데이터를 파싱합니다.
     */
    public ParsedTableData parseBytes(
            byte[] bytes,
            String fileNameHint,
            TabularSourceFormat format,
            String customDelimiter,
            boolean firstRowIsHeader,
            Charset charset
    ) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("입력 데이터 바이트가 비어 있습니다.");
        }
        TabularSourceFormat resolvedFormat = resolveFormat(fileNameHint, format, customDelimiter);
        // PK 시그니처(0x50 0x4B) 확인으로 .xlsx 자동 감지
        if (resolvedFormat == TabularSourceFormat.EXCEL_XLSX || isZipSignature(bytes)) {
            return parseXlsxStream(new ByteArrayInputStream(bytes), firstRowIsHeader);
        }
        Charset effectiveCharset = charset != null ? charset : StandardCharsets.UTF_8;
        String text = new String(bytes, effectiveCharset);
        return parseText(text, resolvedFormat, customDelimiter, firstRowIsHeader);
    }

    /**
     * 텍스트 문자열(CSV, TSV, 파이프 구분 텍스트 등)로부터 테이블 데이터를 파싱합니다.
     */
    public ParsedTableData parseText(
            String rawText,
            TabularSourceFormat format,
            String customDelimiter,
            boolean firstRowIsHeader
    ) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("입력 텍스트 데이터가 비어 있습니다.");
        }
        // UTF-8 BOM 제거
        String cleaned = rawText.startsWith("\uFEFF") ? rawText.substring(1) : rawText;
        String[] lines = cleaned.split("\\r?\\n");
        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                nonEmptyLines.add(line);
            }
        }
        if (nonEmptyLines.isEmpty()) {
            throw new IllegalArgumentException("유효한 데이터 행이 없습니다.");
        }

        TabularSourceFormat resolvedFormat = format == null || format == TabularSourceFormat.AUTO
                ? detectTextFormat(nonEmptyLines.get(0), customDelimiter)
                : format;

        char delimiter = resolveDelimiterChar(resolvedFormat, customDelimiter, nonEmptyLines.get(0));
        List<List<String>> allRows = new ArrayList<>();
        int maxCols = 0;
        for (String line : nonEmptyLines) {
            List<String> parsedRow = parseDelimitedLine(line, delimiter);
            maxCols = Math.max(maxCols, parsedRow.size());
            allRows.add(parsedRow);
        }

        return buildParsedTableData(resolvedFormat, allRows, maxCols, firstRowIsHeader);
    }

    /**
     * 순수 Java ZIP + XML DOM을 이용해 Excel(.xlsx) 첫 번째 워크시트를 파싱합니다.
     */
    public ParsedTableData parseXlsxStream(InputStream xlsxInputStream, boolean firstRowIsHeader) throws IOException {
        Map<String, byte[]> zipEntries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(xlsxInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    zis.transferTo(baos);
                    zipEntries.put(entry.getName(), baos.toByteArray());
                }
            }
        }

        List<String> sharedStrings = parseSharedStrings(zipEntries.get("xl/sharedStrings.xml"));
        byte[] sheetBytes = findFirstWorksheetBytes(zipEntries);
        if (sheetBytes == null) {
            throw new IllegalArgumentException("유효한 Excel(.xlsx) 워크시트(xl/worksheets/sheet*.xml)를 찾을 수 없습니다.");
        }

        List<List<String>> rawRows = parseWorksheetXml(sheetBytes, sharedStrings);
        int maxCols = rawRows.stream().mapToInt(List::size).max().orElse(0);
        return buildParsedTableData(TabularSourceFormat.EXCEL_XLSX, rawRows, maxCols, firstRowIsHeader);
    }

    private ParsedTableData buildParsedTableData(
            TabularSourceFormat format,
            List<List<String>> rawRows,
            int maxCols,
            boolean firstRowIsHeader
    ) {
        if (rawRows.isEmpty() || maxCols == 0) {
            return new ParsedTableData(format, Collections.emptyList(), Collections.emptyList());
        }

        List<String> headers = new ArrayList<>(maxCols);
        List<List<String>> dataRows = new ArrayList<>();

        if (firstRowIsHeader) {
            List<String> headerRow = rawRows.get(0);
            for (int c = 0; c < maxCols; c++) {
                String h = c < headerRow.size() ? headerRow.get(c).trim() : "";
                headers.add(h.isEmpty() ? "COL_" + (c + 1) : h);
            }
            for (int r = 1; r < rawRows.size(); r++) {
                dataRows.add(normalizeRowWidth(rawRows.get(r), maxCols));
            }
        } else {
            for (int c = 0; c < maxCols; c++) {
                headers.add("COL_" + (c + 1));
            }
            for (List<String> row : rawRows) {
                dataRows.add(normalizeRowWidth(row, maxCols));
            }
        }

        return new ParsedTableData(format, Collections.unmodifiableList(headers), Collections.unmodifiableList(dataRows));
    }

    private List<String> normalizeRowWidth(List<String> row, int width) {
        List<String> normalized = new ArrayList<>(width);
        for (int i = 0; i < width; i++) {
            normalized.add(i < row.size() && row.get(i) != null ? row.get(i) : "");
        }
        return Collections.unmodifiableList(normalized);
    }

    private TabularSourceFormat resolveFormat(String fileName, TabularSourceFormat requested, String customDelimiter) {
        if (requested != null && requested != TabularSourceFormat.AUTO) {
            return requested;
        }
        if (customDelimiter != null && !customDelimiter.isEmpty()) {
            return TabularSourceFormat.TXT_CUSTOM;
        }
        if (fileName != null) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".xlsx")) {
                return TabularSourceFormat.EXCEL_XLSX;
            }
            if (lower.endsWith(".csv")) {
                return TabularSourceFormat.CSV;
            }
            if (lower.endsWith(".tsv")) {
                return TabularSourceFormat.TXT_TSV;
            }
        }
        return TabularSourceFormat.AUTO;
    }

    private TabularSourceFormat detectTextFormat(String firstLine, String customDelimiter) {
        if (customDelimiter != null && !customDelimiter.isEmpty()) {
            return TabularSourceFormat.TXT_CUSTOM;
        }
        if (firstLine.contains("\t")) {
            return TabularSourceFormat.TXT_TSV;
        }
        if (firstLine.contains("|") && !firstLine.contains(",")) {
            return TabularSourceFormat.TXT_CUSTOM;
        }
        return TabularSourceFormat.CSV;
    }

    private char resolveDelimiterChar(TabularSourceFormat format, String customDelimiter, String sampleLine) {
        if (customDelimiter != null && !customDelimiter.isEmpty()) {
            return customDelimiter.charAt(0);
        }
        if (format == TabularSourceFormat.TXT_TSV) {
            return '\t';
        }
        if (format == TabularSourceFormat.TXT_CUSTOM && sampleLine.contains("|")) {
            return '|';
        }
        return ',';
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private boolean isZipSignature(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 0x50
                && bytes[1] == 0x4B
                && bytes[2] == 0x03
                && bytes[3] == 0x04;
    }

    private byte[] findFirstWorksheetBytes(Map<String, byte[]> zipEntries) {
        if (zipEntries.containsKey("xl/worksheets/sheet1.xml")) {
            return zipEntries.get("xl/worksheets/sheet1.xml");
        }
        return zipEntries.entrySet().stream()
                .filter(e -> e.getKey().startsWith("xl/worksheets/sheet") && e.getKey().endsWith(".xml"))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<String> parseSharedStrings(byte[] xmlBytes) {
        if (xmlBytes == null) {
            return Collections.emptyList();
        }
        try {
            Document doc = createSafeDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
            NodeList siList = doc.getElementsByTagName("si");
            List<String> result = new ArrayList<>(siList.getLength());
            for (int i = 0; i < siList.getLength(); i++) {
                Element si = (Element) siList.item(i);
                NodeList tNodes = si.getElementsByTagName("t");
                StringBuilder text = new StringBuilder();
                for (int j = 0; j < tNodes.getLength(); j++) {
                    text.append(tNodes.item(j).getTextContent());
                }
                result.add(text.toString());
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Excel sharedStrings.xml 파싱 실패: " + e.getMessage(), e);
        }
    }

    private List<List<String>> parseWorksheetXml(byte[] sheetXmlBytes, List<String> sharedStrings) {
        try {
            Document doc = createSafeDocumentBuilder().parse(new ByteArrayInputStream(sheetXmlBytes));
            NodeList rowNodes = doc.getElementsByTagName("row");
            List<List<String>> rows = new ArrayList<>(rowNodes.getLength());

            for (int r = 0; r < rowNodes.getLength(); r++) {
                Element rowEl = (Element) rowNodes.item(r);
                NodeList cellNodes = rowEl.getElementsByTagName("c");
                List<String> rowValues = new ArrayList<>();
                for (int c = 0; c < cellNodes.getLength(); c++) {
                    Element cellEl = (Element) cellNodes.item(c);
                    String cellRef = cellEl.getAttribute("r"); // e.g., A1, B1, C1
                    int colIndex = parseExcelColumnIndex(cellRef, c);
                    while (rowValues.size() < colIndex) {
                        rowValues.add("");
                    }
                    String cellType = cellEl.getAttribute("t");
                    String value = extractCellValue(cellEl, cellType, sharedStrings);
                    if (rowValues.size() == colIndex) {
                        rowValues.add(value);
                    } else {
                        rowValues.set(colIndex, value);
                    }
                }
                boolean hasNonEmpty = rowValues.stream().anyMatch(v -> v != null && !v.isBlank());
                if (hasNonEmpty) {
                    rows.add(rowValues);
                }
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("Excel worksheet XML 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String extractCellValue(Element cellEl, String cellType, List<String> sharedStrings) {
        if ("inlineStr".equals(cellType)) {
            NodeList tNodes = cellEl.getElementsByTagName("t");
            if (tNodes.getLength() > 0) {
                return tNodes.item(0).getTextContent();
            }
            return "";
        }
        NodeList vNodes = cellEl.getElementsByTagName("v");
        if (vNodes.getLength() == 0) {
            return "";
        }
        String rawVal = vNodes.item(0).getTextContent();
        if ("s".equals(cellType)) {
            int idx = Integer.parseInt(rawVal.trim());
            return idx >= 0 && idx < sharedStrings.size() ? sharedStrings.get(idx) : rawVal;
        }
        return rawVal;
    }

    private int parseExcelColumnIndex(String cellRef, int fallbackIndex) {
        if (cellRef == null || cellRef.isEmpty()) {
            return fallbackIndex;
        }
        int col = 0;
        int i = 0;
        while (i < cellRef.length() && Character.isLetter(cellRef.charAt(i))) {
            col = col * 26 + (Character.toUpperCase(cellRef.charAt(i)) - 'A' + 1);
            i++;
        }
        return col > 0 ? col - 1 : fallbackIndex;
    }

    private DocumentBuilder createSafeDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder();
    }
}
