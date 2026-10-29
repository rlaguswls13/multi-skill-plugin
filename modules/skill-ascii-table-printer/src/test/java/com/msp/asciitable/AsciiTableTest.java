package com.msp.asciitable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AsciiTable Unit Tests")
class AsciiTableTest {

    @Nested
    @DisplayName("Table Styles")
    class TableStyleTests {

        @Test
        @DisplayName("ASCII style table rendering")
        void testAsciiStyle() {
            String table = AsciiTable.builder()
                    .style(TableStyle.ASCII)
                    .header("ID", "Name")
                    .row("1", "Alice")
                    .row("2", "Bob")
                    .render();

            assertTrue(table.contains("+----+-------+"));
            assertTrue(table.contains("| ID | Name  |"));
            assertTrue(table.contains("| 1  | Alice |"));
            assertTrue(table.contains("| 2  | Bob   |"));
        }

        @Test
        @DisplayName("Unicode Box style table rendering")
        void testUnicodeBoxStyle() {
            String table = AsciiTable.builder()
                    .style(TableStyle.UNICODE_BOX)
                    .header("ID", "Status")
                    .row("101", "OK")
                    .row("102", "FAIL")
                    .render();

            assertTrue(table.startsWith("┌─────┬────────┐"));
            assertTrue(table.contains("│ ID  │ Status │"));
            assertTrue(table.contains("├─────┼────────┤"));
            assertTrue(table.contains("│ 101 │ OK     │"));
            assertTrue(table.endsWith("└─────┴────────┘\n"));
        }

        @Test
        @DisplayName("Markdown style table rendering with alignments")
        void testMarkdownStyle() {
            String table = AsciiTable.builder()
                    .style(TableStyle.MARKDOWN)
                    .header("Rank", "Player", "Score")
                    .alignment(0, ColumnAlignment.CENTER)
                    .alignment(1, ColumnAlignment.LEFT)
                    .alignment(2, ColumnAlignment.RIGHT)
                    .row("1", "Neo", "9500")
                    .row("2", "Trinity", "9200")
                    .render();

            assertTrue(table.contains("| Rank | Player  | Score |"));
            assertTrue(table.contains("|:----:|:--------|------:|"));
            assertTrue(table.contains("|  1   | Neo     |  9500 |"));
            assertTrue(table.contains("|  2   | Trinity |  9200 |"));
        }

        @Test
        @DisplayName("Minimal style table rendering")
        void testMinimalStyle() {
            String table = AsciiTable.builder()
                    .style(TableStyle.MINIMAL)
                    .header("KEY", "VALUE")
                    .row("host", "localhost")
                    .render();

            assertFalse(table.contains("+"));
            assertFalse(table.contains("|"));
            assertTrue(table.contains("KEY"));
            assertTrue(table.contains("localhost"));
        }
    }

    @Nested
    @DisplayName("Alignments and Formatting")
    class AlignmentTests {

        @Test
        @DisplayName("Column alignment check (LEFT, CENTER, RIGHT)")
        void testAlignments() {
            String table = AsciiTable.builder()
                    .style(TableStyle.ASCII)
                    .header("Left", "Center", "Right")
                    .alignment(0, ColumnAlignment.LEFT)
                    .alignment(1, ColumnAlignment.CENTER)
                    .alignment(2, ColumnAlignment.RIGHT)
                    .row("a", "b", "c")
                    .render();

            assertTrue(table.contains("| Left | Center | Right |"));
            assertTrue(table.contains("| a    |   b    |     c |"));
        }

        @Test
        @DisplayName("East Asian Width display calculation (Korean characters)")
        void testEastAsianWidth() {
            assertEquals(4, AsciiTableBuilder.displayWidth("한글"));
            assertEquals(5, AsciiTableBuilder.displayWidth("Java8"));
            assertEquals(9, AsciiTableBuilder.displayWidth("Java 한글"));

            String table = AsciiTable.builder()
                    .style(TableStyle.ASCII)
                    .header("이름", "직급")
                    .row("홍길동", "팀장")
                    .row("Kim", "사원")
                    .render();

            assertTrue(table.contains("| 이름   | 직급 |"));
            assertTrue(table.contains("| 홍길동 | 팀장 |"));
            assertTrue(table.contains("| Kim    | 사원 |"));
        }
    }

    @Nested
    @DisplayName("Multi-line Cells & Edge Cases")
    class MultiLineAndEdgeCases {

        @Test
        @DisplayName("Multi-line cell breaks lines cleanly")
        void testMultiLineCells() {
            String table = AsciiTable.builder()
                    .style(TableStyle.ASCII)
                    .header("Item", "Description")
                    .row("Coffee", "Hot brewed\nEthiopian Yirgacheffe")
                    .row("Tea", "Green tea")
                    .render();

            assertTrue(table.contains("Hot brewed"));
            assertTrue(table.contains("Ethiopian Yirgacheffe"));
            // The row for Coffee spans 2 lines, second line has empty first column
            assertTrue(table.contains("| Coffee | Hot brewed            |"));
            assertTrue(table.contains("|        | Ethiopian Yirgacheffe |"));
        }

        @Test
        @DisplayName("Null values and empty tables")
        void testNullAndEmpty() {
            assertEquals("", AsciiTable.builder().render());

            String table = AsciiTable.builder()
                    .style(TableStyle.ASCII)
                    .nullPlaceholder("N/A")
                    .header("ID", "Value")
                    .row("1", null)
                    .render();

            assertTrue(table.contains("| 1  | N/A   |"));
        }
    }
}
