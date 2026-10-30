package com.msp.excel;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * High-performance, zero-dependency Excel (.xlsx) reader using pure Java StAX XML parser and ZipInputStream.
 */
public final class FastExcelReader {

    private static final XMLInputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLInputFactory.newInstance();
        // Secure against XXE
        XML_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XML_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    private FastExcelReader() {}

    /**
     * Reads all rows from the primary sheet of an Excel (.xlsx) input stream.
     */
    public static List<ExcelRow> readRows(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");

        Map<String, byte[]> entries = extractZipEntries(inputStream);

        List<String> sharedStrings = Collections.emptyList();
        byte[] sharedStringsBytes = entries.get("xl/sharedStrings.xml");
        if (sharedStringsBytes != null) {
            sharedStrings = parseSharedStrings(sharedStringsBytes);
        }

        // Find primary sheet (usually xl/worksheets/sheet1.xml)
        byte[] sheetBytes = entries.get("xl/worksheets/sheet1.xml");
        if (sheetBytes == null) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (entry.getKey().startsWith("xl/worksheets/sheet") && entry.getKey().endsWith(".xml")) {
                    sheetBytes = entry.getValue();
                    break;
                }
            }
        }

        if (sheetBytes == null) {
            return Collections.emptyList();
        }

        return parseSheet(sheetBytes, sharedStrings);
    }

    /**
     * Reads all rows from byte array.
     */
    public static List<ExcelRow> readRows(byte[] xlsxBytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(xlsxBytes)) {
            return readRows(bais);
        }
    }

    private static Map<String, byte[]> extractZipEntries(InputStream is) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    map.put(entry.getName(), baos.toByteArray());
                }
                zis.closeEntry();
            }
        }
        return map;
    }

    private static List<String> parseSharedStrings(byte[] xmlBytes) throws IOException {
        List<String> list = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(xmlBytes)) {
            XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(bais);
            StringBuilder sb = new StringBuilder();
            boolean insideText = false;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("t".equals(reader.getLocalName())) {
                        insideText = true;
                        sb.setLength(0);
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && insideText) {
                    sb.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("t".equals(reader.getLocalName())) {
                        insideText = false;
                        list.add(sb.toString());
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("Failed to parse sharedStrings.xml", e);
        }
        return list;
    }

    private static List<ExcelRow> parseSheet(byte[] sheetXml, List<String> sharedStrings) throws IOException {
        List<ExcelRow> rows = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(sheetXml)) {
            XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(bais);

            int currentRowNumber = 0;
            List<String> currentCells = new ArrayList<>();
            int expectedCol = 0;
            String cellType = null;
            StringBuilder cellValue = new StringBuilder();
            boolean insideValue = false;
            boolean insideInlineText = false;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("row".equals(localName)) {
                        String rAttr = reader.getAttributeValue(null, "r");
                        currentRowNumber = rAttr != null ? Integer.parseInt(rAttr) : rows.size() + 1;
                        currentCells = new ArrayList<>();
                        expectedCol = 0;
                    } else if ("c".equals(localName)) {
                        cellType = reader.getAttributeValue(null, "t");
                        String cellRef = reader.getAttributeValue(null, "r");
                        if (cellRef != null) {
                            int actualCol = colRefToIndex(cellRef);
                            while (expectedCol < actualCol) {
                                currentCells.add("");
                                expectedCol++;
                            }
                        }
                        cellValue.setLength(0);
                    } else if ("v".equals(localName)) {
                        insideValue = true;
                    } else if ("t".equals(localName)) {
                        insideInlineText = true;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    if (insideValue || insideInlineText) {
                        cellValue.append(reader.getText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("v".equals(localName)) {
                        insideValue = false;
                    } else if ("t".equals(localName)) {
                        insideInlineText = false;
                    } else if ("c".equals(localName)) {
                        String rawVal = cellValue.toString().trim();
                        String resolvedVal = rawVal;
                        if ("s".equals(cellType)) {
                            try {
                                int sstIndex = Integer.parseInt(rawVal);
                                if (sstIndex >= 0 && sstIndex < sharedStrings.size()) {
                                    resolvedVal = sharedStrings.get(sstIndex);
                                }
                            } catch (NumberFormatException ignored) {}
                        } else if ("b".equals(cellType)) {
                            resolvedVal = "1".equals(rawVal) ? "true" : "false";
                        }
                        currentCells.add(resolvedVal);
                        expectedCol++;
                        cellType = null;
                    } else if ("row".equals(localName)) {
                        rows.add(new ExcelRow(currentRowNumber, currentCells));
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("Failed to parse sheet XML", e);
        }
        return rows;
    }

    private static int colRefToIndex(String cellRef) {
        int index = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = cellRef.charAt(i);
            if (Character.isLetter(ch)) {
                index = index * 26 + (Character.toUpperCase(ch) - 'A' + 1);
            } else {
                break;
            }
        }
        return index - 1;
    }
}
