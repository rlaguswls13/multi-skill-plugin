package com.msp.excel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FastExcelReaderTest {

    @Test
    @DisplayName("Read simple table rows from pure in-memory generated .xlsx")
    void testReadRows() throws IOException {
        List<List<String>> inputData = List.of(
                List.of("ID", "Name", "Score"),
                List.of("1", "Alice", "95.5"),
                List.of("2", "Bob", "88.0"),
                List.of("3", "Charlie", "100.0")
        );

        byte[] xlsxBytes = FastExcelWriter.createXlsx(inputData);
        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);

        List<ExcelRow> rows = FastExcelReader.readRows(xlsxBytes);
        assertEquals(4, rows.size());

        // Header check
        ExcelRow header = rows.get(0);
        assertEquals(1, header.rowNumber());
        assertEquals(3, header.size());
        assertEquals("ID", header.getCell(0));
        assertEquals("Name", header.getCell(1));
        assertEquals("Score", header.getCell(2));

        // Row 2 check
        ExcelRow row2 = rows.get(1);
        assertEquals(2, row2.rowNumber());
        assertEquals("1", row2.getCell(0));
        assertEquals("Alice", row2.getCell(1));
        assertEquals("95.5", row2.getCell(2));

        // Out of bound check
        assertEquals("", row2.getCell(99));
    }
}
