package com.msp.excel;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a parsed row from an Excel (.xlsx) spreadsheet.
 *
 * @param rowNumber 1-based row index in the sheet
 * @param cells      list of string values for each column cell in the row
 */
public record ExcelRow(int rowNumber, List<String> cells) {

    public ExcelRow {
        Objects.requireNonNull(cells, "cells must not be null");
        cells = List.copyOf(cells);
    }

    /**
     * Gets cell value by 0-based column index, or empty string if index out of bounds.
     */
    public String getCell(int colIndex) {
        if (colIndex < 0 || colIndex >= cells.size()) {
            return "";
        }
        return cells.get(colIndex);
    }

    /**
     * Number of cells in this row.
     */
    public int size() {
        return cells.size();
    }
}
