package com.msp.ptyterm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerminalScreenBufferTest {

    @Test
    @DisplayName("AnsiEscapeFilter: removes color codes and title sequences")
    void testAnsiFilter() {
        String colored = "\u001B[31mError:\u001B[0m \u001B[1;33mNullPointerException\u001B[0m in line 42";
        assertEquals("Error: NullPointerException in line 42", AnsiEscapeFilter.stripAnsi(colored));

        String withOsc = "\u001B]0;Build in progress...\u0007Compiling 5 files...";
        assertEquals("Compiling 5 files...", AnsiEscapeFilter.stripAnsi(withOsc));

        String mixed = "\u001B[2J\u001B[1;1HWelcome to CLI!";
        assertEquals("Welcome to CLI!", AnsiEscapeFilter.stripAnsi(mixed));
    }

    @Test
    @DisplayName("TerminalScreenBuffer: progress bar simulation using carriage return (\\r)")
    void testProgressBarCarriageReturn() {
        TerminalScreenBuffer term = new TerminalScreenBuffer(80, 5);

        term.write("Downloading: [=>     ] 20%\r");
        term.write("Downloading: [===>   ] 50%\r");
        term.write("Downloading: [======>] 100%\nDone!");

        List<String> lines = term.getLines();
        assertEquals(2, lines.size());
        assertEquals("Downloading: [======>] 100%", lines.get(0));
        assertEquals("Done!", lines.get(1));
    }

    @Test
    @DisplayName("TerminalScreenBuffer: backspace (\\b) overwriting")
    void testBackspace() {
        TerminalScreenBuffer term = new TerminalScreenBuffer(40, 5);
        term.write("Passwrod\b\b\bord");

        assertEquals("Password", term.getLine(0));
    }

    @Test
    @DisplayName("TerminalScreenBuffer: tab (\\t) alignment to 8-col tab stops")
    void testTabs() {
        TerminalScreenBuffer term = new TerminalScreenBuffer(40, 5);
        term.write("A\tB\tC");

        // 'A' at col 0, next tab stop is col 8 ('B'), next is col 16 ('C')
        assertEquals("A       B       C", term.getLine(0));
    }

    @Test
    @DisplayName("TerminalScreenBuffer: ANSI cursor movement and line clear (\\u001B[2K)")
    void testCursorMovementAndLineClear() {
        TerminalScreenBuffer term = new TerminalScreenBuffer(40, 5);

        term.writeln("Line 1");
        term.writeln("Line 2 will be erased");
        term.writeln("Line 3");

        // Move cursor up to Line 2: ESC [ 1 A (or ESC [ 2;1 H)
        term.write("\u001B[2;1H");
        assertEquals(1, term.getCursorRow());

        // Clear line: ESC [ 2 K
        term.write("\u001B[2K");
        assertEquals("", term.getLine(1));

        // Write replacement
        term.write("Replaced Line 2");
        assertEquals("Replaced Line 2", term.getLine(1));
    }

    @Test
    @DisplayName("TerminalScreenBuffer: Screen clear (\\u001B[2J)")
    void testScreenClear() {
        TerminalScreenBuffer term = new TerminalScreenBuffer(40, 5);
        term.writeln("Hello");
        term.writeln("World");

        assertEquals(2, term.getLines().size());

        term.write("\u001B[2J");
        assertTrue(term.getLines().isEmpty());
        assertEquals("", term.getScreenText());
    }

    @Test
    @DisplayName("TerminalScreenBuffer: screen scrolling when exceeding max height")
    void testScreenScrolling() {
        TerminalScreenBuffer term = new TerminalScreenBuffer(40, 3);

        term.writeln("Line 1");
        term.writeln("Line 2");
        term.writeln("Line 3");
        term.write("Line 4"); // Causes scroll up by 1

        List<String> lines = term.getLines();
        assertEquals(3, lines.size());
        assertEquals("Line 2", lines.get(0));
        assertEquals("Line 3", lines.get(1));
        assertEquals("Line 4", lines.get(2));

        // Adding a newline after Line 4 scrolls it up to row 1
        term.newLine();
        term.write("Line 5");
        List<String> scrolledAgain = term.getLines();
        assertEquals(3, scrolledAgain.size());
        assertEquals("Line 3", scrolledAgain.get(0));
        assertEquals("Line 4", scrolledAgain.get(1));
        assertEquals("Line 5", scrolledAgain.get(2));
    }

    @Test
    @DisplayName("TerminalScreenBuffer: ANSI color codes are filtered transparently")
    void testColorStrippingInBuffer() {
        TerminalScreenBuffer term = new TerminalScreenBuffer(80, 5);
        term.writeln("\u001B[32mSUCCESS:\u001B[0m All tests passed!");

        assertEquals("SUCCESS: All tests passed!", term.getLine(0));
    }
}
