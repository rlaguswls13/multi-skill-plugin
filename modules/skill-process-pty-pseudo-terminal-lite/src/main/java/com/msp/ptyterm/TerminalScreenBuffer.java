package com.msp.ptyterm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Virtual terminal screen emulator and line buffer for interactive CLI processes.
 * Simulates carriage return (\r), backspace (\b), newlines (\n), ANSI cursor positioning,
 * line clears (\u001B[2K), screen clears (\u001B[2J), and color stripping.
 */
public final class TerminalScreenBuffer {

    private final int width;
    private final int height;
    private final char[][] grid;
    private int cursorRow = 0;
    private int cursorCol = 0;

    /**
     * Creates a terminal buffer with standard default dimensions (80 cols, 24 rows).
     */
    public TerminalScreenBuffer() {
        this(80, 24);
    }

    /**
     * Creates a terminal buffer with specified dimensions.
     */
    public TerminalScreenBuffer(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.grid = new char[height][width];
        clearScreen();
    }

    /**
     * Writes a string of CLI process output into the virtual terminal screen.
     */
    public synchronized TerminalScreenBuffer write(String text) {
        if (text == null || text.isEmpty()) {
            return this;
        }

        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Handle ANSI Escape sequences
            if (c == '\u001B') {
                if (i + 1 < len) {
                    char next = text.charAt(i + 1);
                    if (next == '[') {
                        // CSI sequence: parse until command letter
                        int j = i + 2;
                        while (j < len && (Character.isDigit(text.charAt(j)) || text.charAt(j) == ';' || text.charAt(j) == '?')) {
                            j++;
                        }
                        if (j < len) {
                            char cmd = text.charAt(j);
                            String paramStr = text.substring(i + 2, j);
                            handleCsiSequence(cmd, paramStr);
                            i = j + 1;
                            continue;
                        }
                    } else if (next == ']') {
                        // OSC sequence: search for BEL (\u0007) or ST (\u001B\\)
                        int j = i + 2;
                        while (j < len && text.charAt(j) != '\u0007') {
                            if (text.charAt(j) == '\u001B' && j + 1 < len && text.charAt(j + 1) == '\\') {
                                j += 2;
                                break;
                            }
                            j++;
                        }
                        if (j < len && text.charAt(j) == '\u0007') {
                            j++;
                        }
                        i = j;
                        continue;
                    } else if (next == '(' || next == ')' || next == '#' || next == '%') {
                        // 3-byte escape
                        i = Math.min(len, i + 3);
                        continue;
                    } else {
                        // 2-byte escape
                        i += 2;
                        continue;
                    }
                }
                i++;
                continue;
            }

            // Handle control characters
            switch (c) {
                case '\r' -> cursorCol = 0;
                case '\n' -> newLine();
                case '\b' -> {
                    if (cursorCol > 0) {
                        cursorCol--;
                    }
                }
                case '\t' -> {
                    int nextTab = (cursorCol / 8 + 1) * 8;
                    cursorCol = Math.min(width - 1, nextTab);
                }
                case '\f' -> clearScreen();
                default -> {
                    if (c >= 32 || Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
                        putChar(c);
                    }
                }
            }
            i++;
        }
        return this;
    }

    /**
     * Appends a line followed by a newline.
     */
    public synchronized TerminalScreenBuffer writeln(String line) {
        write(line);
        newLine();
        return this;
    }

    private void putChar(char c) {
        if (cursorCol >= width) {
            cursorCol = 0;
            cursorRow++;
            if (cursorRow >= height) {
                scrollUp(1);
                cursorRow = height - 1;
            }
        }
        grid[cursorRow][cursorCol] = c;
        cursorCol++;
    }

    /**
     * Advances cursor to the beginning of the next line, scrolling up if necessary.
     */
    public synchronized TerminalScreenBuffer newLine() {
        cursorCol = 0;
        cursorRow++;
        if (cursorRow >= height) {
            scrollUp(1);
            cursorRow = height - 1;
        }
        return this;
    }

    private void scrollUp(int count) {
        for (int k = 0; k < count; k++) {
            for (int r = 0; r < height - 1; r++) {
                System.arraycopy(grid[r + 1], 0, grid[r], 0, width);
            }
            Arrays.fill(grid[height - 1], ' ');
        }
    }

    private void handleCsiSequence(char cmd, String params) {
        switch (cmd) {
            case 'm' -> {
                // SGR color / style sequence: ignored in plain buffer
            }
            case 'H', 'f' -> {
                // Cursor position: ESC [ <row> ; <col> H (1-indexed)
                int r = 1;
                int c = 1;
                if (!params.isEmpty()) {
                    String[] parts = params.split(";");
                    if (parts.length > 0 && !parts[0].isBlank()) {
                        try { r = Integer.parseInt(parts[0].trim()); } catch (NumberFormatException ignored) {}
                    }
                    if (parts.length > 1 && !parts[1].isBlank()) {
                        try { c = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
                    }
                }
                cursorRow = Math.max(0, Math.min(height - 1, r - 1));
                cursorCol = Math.max(0, Math.min(width - 1, c - 1));
            }
            case 'A' -> { // Cursor Up
                int n = parseSingleParam(params, 1);
                cursorRow = Math.max(0, cursorRow - n);
            }
            case 'B' -> { // Cursor Down
                int n = parseSingleParam(params, 1);
                cursorRow = Math.min(height - 1, cursorRow + n);
            }
            case 'C' -> { // Cursor Forward (Right)
                int n = parseSingleParam(params, 1);
                cursorCol = Math.min(width - 1, cursorCol + n);
            }
            case 'D' -> { // Cursor Back (Left)
                int n = parseSingleParam(params, 1);
                cursorCol = Math.max(0, cursorCol - n);
            }
            case 'K' -> { // Erase in Line
                int mode = parseSingleParam(params, 0);
                if (mode == 0) {
                    // 0: clear from cursor to end of line
                    Arrays.fill(grid[cursorRow], cursorCol, width, ' ');
                } else if (mode == 1) {
                    // 1: clear from start of line to cursor
                    Arrays.fill(grid[cursorRow], 0, Math.min(width, cursorCol + 1), ' ');
                } else if (mode == 2) {
                    // 2: clear entire line
                    Arrays.fill(grid[cursorRow], ' ');
                }
            }
            case 'J' -> { // Erase in Display
                int mode = parseSingleParam(params, 0);
                if (mode == 2) {
                    clearScreen();
                } else if (mode == 0) {
                    // from cursor to end of screen
                    Arrays.fill(grid[cursorRow], cursorCol, width, ' ');
                    for (int r = cursorRow + 1; r < height; r++) {
                        Arrays.fill(grid[r], ' ');
                    }
                } else if (mode == 1) {
                    // from start of screen to cursor
                    for (int r = 0; r < cursorRow; r++) {
                        Arrays.fill(grid[r], ' ');
                    }
                    Arrays.fill(grid[cursorRow], 0, Math.min(width, cursorCol + 1), ' ');
                }
            }
            default -> {
                // Ignore unsupported CSI sequences
            }
        }
    }

    private int parseSingleParam(String params, int def) {
        if (params == null || params.isBlank()) return def;
        try {
            return Integer.parseInt(params.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Clears the screen and resets cursor to top-left (0, 0).
     */
    public synchronized void clearScreen() {
        for (int r = 0; r < height; r++) {
            Arrays.fill(grid[r], ' ');
        }
        cursorRow = 0;
        cursorCol = 0;
    }

    /**
     * Returns full screen text with trailing empty lines and spaces trimmed.
     */
    public synchronized String getScreenText() {
        List<String> lines = getLines();
        return String.join("\n", lines);
    }

    /**
     * Returns all active lines as a list of strings, with trailing whitespace trimmed.
     */
    public synchronized List<String> getLines() {
        List<String> result = new ArrayList<>();
        int lastNonEmpty = -1;
        for (int r = 0; r < height; r++) {
            String line = getLine(r);
            result.add(line);
            if (!line.isEmpty()) {
                lastNonEmpty = r;
            }
        }
        if (lastNonEmpty == -1) {
            return List.of();
        }
        return Collections.unmodifiableList(result.subList(0, lastNonEmpty + 1));
    }

    /**
     * Returns the text of a specific line (0-indexed) with trailing spaces trimmed.
     */
    public synchronized String getLine(int row) {
        if (row < 0 || row >= height) {
            throw new IndexOutOfBoundsException("Row: " + row + ", height: " + height);
        }
        int lastChar = -1;
        for (int c = width - 1; c >= 0; c--) {
            if (grid[row][c] != ' ') {
                lastChar = c;
                break;
            }
        }
        if (lastChar == -1) {
            return "";
        }
        return new String(grid[row], 0, lastChar + 1);
    }

    public synchronized int getCursorRow() {
        return cursorRow;
    }

    public synchronized int getCursorCol() {
        return cursorCol;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
