package com.msp.csv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CsvStreamerTest {

    @Test
    @DisplayName("Should read standard CSV with headers and typed accessors")
    void testBasicCsvReading() {
        String csv = """
                id,name,age,active,score
                1,Alice,30,true,98.5
                2,Bob,25,false,82.0
                3,Charlie,40,yes,75.25
                """;

        try (CsvStreamReader reader = CsvStreamer.readString(csv)) {
            assertEquals(List.of("id", "name", "age", "active", "score"), reader.headers());

            List<CsvRow> rows = reader.stream().toList();
            assertEquals(3, rows.size());

            CsvRow row1 = rows.get(0);
            assertEquals(1, row1.rowNumber());
            assertEquals("1", row1.get("id"));
            assertEquals("Alice", row1.get("name"));
            assertEquals(30, row1.getAsInt("age"));
            assertTrue(row1.getAsBoolean("active"));
            assertEquals(98.5, row1.getAsDouble("score"), 0.001);

            CsvRow row2 = rows.get(1);
            assertEquals(2, row2.getAsInt("id"));
            assertEquals("Bob", row2.get("name"));
            assertFalse(row2.getAsBoolean("active"));

            CsvRow row3 = rows.get(2);
            assertTrue(row3.getAsBoolean("active"));
        }
    }

    @Test
    @DisplayName("Should correctly handle complex quoted fields, escaped quotes, and embedded newlines")
    void testComplexQuotedFields() {
        String csv = "id,comment,tags\n"
                + "1,\"Line 1\nLine 2\",\"tag1,tag2\"\n"
                + "2,\"He said \"\"Hello!\"\"\",\"quote\"\n"
                + "3,\"\",\"empty\"\n";

        try (CsvStreamReader reader = CsvStreamer.readString(csv)) {
            List<CsvRow> rows = reader.stream().toList();
            assertEquals(3, rows.size());

            // Embedded newline
            assertEquals("Line 1\nLine 2", rows.get(0).get("comment").replace("\r\n", "\n"));
            assertEquals("tag1,tag2", rows.get(0).get("tags"));

            // Escaped quote: "" -> "
            assertEquals("He said \"Hello!\"", rows.get(1).get("comment"));

            // Empty quoted string
            assertEquals("", rows.get(2).get("comment"));
        }
    }

    @Test
    @DisplayName("Should support TSV reading and writing with tab delimiters")
    void testTsvFormat() {
        String tsv = "user_id\tuser_name\trole\n101\tadmin_user\tADMIN\n102\tguest_user\tGUEST\n";

        try (CsvStreamReader reader = CsvStreamer.readTsvString(tsv)) {
            assertEquals(List.of("user_id", "user_name", "role"), reader.headers());
            List<CsvRow> rows = reader.stream().toList();
            assertEquals(2, rows.size());
            assertEquals("admin_user", rows.get(0).get("user_name"));
            assertEquals("GUEST", rows.get(1).get("role"));
        }

        // Test TSV writing
        String output = CsvStreamer.writeToString(writer -> {
            try {
                writer.writeHeader("col1", "col2");
                writer.writeRow("val\t1", "val 2");
            } catch (IOException e) {
                fail(e);
            }
        }, CsvFormat.TSV);

        assertTrue(output.contains("\"val\t1\"\tval 2"));
    }

    @Test
    @DisplayName("Should support custom delimiters such as semicolon and pipe")
    void testCustomDelimiter() {
        String data = "name|dept|salary\nAlice|Engineering|95000\nBob|Marketing|70000\n";
        CsvFormat pipeFormat = CsvFormat.builder().delimiter('|').lineSeparator("\n").build();

        try (CsvStreamReader reader = CsvStreamReader.of(data, pipeFormat)) {
            List<CsvRow> rows = reader.stream().toList();
            assertEquals(2, rows.size());
            assertEquals("Engineering", rows.get(0).get("dept"));
            assertEquals(95000, rows.get(0).getAsInt("salary"));
            assertEquals("Marketing", rows.get(1).get("dept"));
        }
    }

    @Test
    @DisplayName("Should stream large dataset without loading all rows into memory")
    void testLargeDatasetStreaming(@TempDir Path tempDir) throws IOException {
        Path largeCsv = tempDir.resolve("large.csv");
        int rowCount = 50_000;

        // Write large dataset
        try (CsvStreamWriter writer = CsvStreamer.write(largeCsv)) {
            writer.writeHeader("index", "name", "random_num");
            for (int i = 1; i <= rowCount; i++) {
                writer.writeRow(i, "User_" + i, i * 2);
            }
        }

        // Stream large dataset row-by-row
        AtomicInteger processedCount = new AtomicInteger();
        long totalSum = 0;

        try (CsvStreamReader reader = CsvStreamer.read(largeCsv)) {
            Iterator<CsvRow> iterator = reader.iterator();
            while (iterator.hasNext()) {
                CsvRow row = iterator.next();
                int idx = row.getAsInt("index");
                int rand = row.getAsInt("random_num");
                assertEquals(idx * 2, rand);
                totalSum += idx;
                processedCount.incrementAndGet();
            }
        }

        assertEquals(rowCount, processedCount.get());
        assertEquals((long) rowCount * (rowCount + 1) / 2, totalSum);
    }

    @Test
    @DisplayName("Should write Map rows using established headers")
    void testWriteMapRows() {
        String result = CsvStreamer.writeToString(writer -> {
            try {
                writer.writeHeader("id", "title", "price");
                writer.writeRow(Map.of("id", 1, "title", "Book", "price", 15000));
                writer.writeRow(Map.of("id", 2, "title", "Pen, Black", "price", 1000));
            } catch (IOException e) {
                fail(e);
            }
        });

        assertTrue(result.contains("id,title,price"));
        assertTrue(result.contains("1,Book,15000"));
        assertTrue(result.contains("2,\"Pen, Black\",1000"));
    }

    @Test
    @DisplayName("Should convert CsvRow to Map cleanly")
    void testRowToMap() {
        String csv = "key,value\nenv,production\nversion,1.0.0\n";
        try (CsvStreamReader reader = CsvStreamer.readString(csv)) {
            List<Map<String, String>> maps = reader.stream().map(CsvRow::toMap).toList();
            assertEquals(2, maps.size());
            assertEquals("production", maps.get(0).get("value"));
            assertEquals("1.0.0", maps.get(1).get("value"));
        }
    }
}
