package com.msp.csvschema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class CsvSchemaValidatorTest {

    @Test
    @DisplayName("Valid CSV passes all schema constraints")
    void testValidCsv() throws Exception {
        String csv = "id,name,email,age\n"
                + "1,Alice,alice@example.com,25\n"
                + "2,Bob,bob@example.com,30\n";

        CsvSchema schema = CsvSchema.builder()
                .column("id", DataType.INTEGER, true)
                .column("name", DataType.STRING, true)
                .column("email", DataType.EMAIL, true)
                .column(ColumnRule.builder("age", DataType.INTEGER).range(1, 120).required(true).build())
                .build();

        ValidationReport report = CsvSchemaValidator.validate(new StringReader(csv), schema);
        assertTrue(report.isValid());
        assertEquals(0, report.errorCount());
        assertEquals(2, report.totalRows());
    }

    @Test
    @DisplayName("Invalid CSV produces detailed row-level error report")
    void testInvalidCsv() throws Exception {
        String csv = "id,name,email,age\n"
                + "1,Alice,not-an-email,25\n"
                + "NaN,Bob,bob@example.com,150\n" // invalid int, age > 120
                + ",Charlie,charlie@example.com,30\n"; // missing required id

        CsvSchema schema = CsvSchema.builder()
                .column("id", DataType.INTEGER, true)
                .column("name", DataType.STRING, true)
                .column("email", DataType.EMAIL, true)
                .column(ColumnRule.builder("age", DataType.INTEGER).range(1, 120).required(true).build())
                .build();

        ValidationReport report = CsvSchemaValidator.validate(new StringReader(csv), schema);
        assertFalse(report.isValid());
        assertEquals(4, report.errorCount());

        // Check error row numbers
        assertTrue(report.errors().stream().anyMatch(e -> e.rowNumber() == 2 && e.columnName().equals("email")));
        assertTrue(report.errors().stream().anyMatch(e -> e.rowNumber() == 3 && e.columnName().equals("id")));
        assertTrue(report.errors().stream().anyMatch(e -> e.rowNumber() == 3 && e.columnName().equals("age")));
        assertTrue(report.errors().stream().anyMatch(e -> e.rowNumber() == 4 && e.columnName().equals("id")));
    }
}
