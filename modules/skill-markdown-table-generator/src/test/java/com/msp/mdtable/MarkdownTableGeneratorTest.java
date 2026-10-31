package com.msp.mdtable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownTableGeneratorTest {

    @Test
    @DisplayName("기본 테이블 생성 및 정렬/구분자 검증")
    void testBasicTableGeneration() {
        MarkdownTableBuilder builder = new MarkdownTableBuilder()
                .addColumn("ID", TableAlignment.RIGHT)
                .addColumn("Name", TableAlignment.LEFT)
                .addColumn("Status", TableAlignment.CENTER)
                .addRow(1, "Alice", "ACTIVE")
                .addRow(20, "Bob", "PENDING")
                .addRow(300, "Charlie", "DONE");

        String md = builder.toMarkdown();
        assertNotNull(md);
        assertTrue(md.contains("|   ID | Name    | Status  |"));
        assertTrue(md.contains("| ---: | :------ | :-----: |"));
        assertTrue(md.contains("|    1 | Alice   | ACTIVE  |"));
        assertTrue(md.contains("|   20 | Bob     | PENDING |"));
        assertTrue(md.contains("|  300 | Charlie |  DONE   |"));
    }

    @Test
    @DisplayName("Map 리스트로부터 자동 테이블 생성 (fromMaps)")
    void testFromMaps() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("Skill", "Markdown");
        row1.put("Score", 95);

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("Skill", "Java 21");
        row2.put("Score", 100);

        List<String> headers = List.of("Skill", "Score");
        Map<String, TableAlignment> aligns = Map.of(
                "Skill", TableAlignment.LEFT,
                "Score", TableAlignment.RIGHT
        );

        MarkdownTableBuilder builder = MarkdownTableBuilder.fromMaps(List.of(row1, row2), headers, aligns);

        assertEquals(2, builder.getRowCount());
        assertEquals(2, builder.getColumnCount());

        String md = builder.toMarkdown();
        assertTrue(md.contains("| Skill    | Score |"));
        assertTrue(md.contains("| :------- | ----: |"));
        assertTrue(md.contains("| Markdown |    95 |"));
        assertTrue(md.contains("| Java 21  |   100 |"));
    }

    @Test
    @DisplayName("Null 값 및 열 개수 불일치 방어 처리")
    void testNullAndMismatchedColumns() {
        MarkdownTableBuilder builder = new MarkdownTableBuilder()
                .addColumn("Col1")
                .addColumn("Col2")
                .addRow("A") // Col2 누락
                .addRow(null, "B"); // Col1 null

        String md = builder.toMarkdown();
        assertTrue(md.contains("| A    |      |"));
        assertTrue(md.contains("|      | B    |"));
    }

    @Test
    @DisplayName("파이프 기호 이스케이프 및 개행 변환")
    void testPipeAndNewlineEscaping() {
        MarkdownTableBuilder builder = new MarkdownTableBuilder()
                .addColumn("Expression")
                .addRow("A | B\nLine2");

        String md = builder.toMarkdown();
        assertTrue(md.contains("A \\| B<br>Line2"));
    }

    @Test
    @DisplayName("열이 없는 경우 빈 문자열 반환")
    void testEmptyColumns() {
        MarkdownTableBuilder builder = new MarkdownTableBuilder();
        assertEquals("", builder.toMarkdown());
    }

    @Test
    @DisplayName("clearRows 동작 확인")
    void testClearRows() {
        MarkdownTableBuilder builder = new MarkdownTableBuilder()
                .addColumn("Col")
                .addRow("Data1");
        assertEquals(1, builder.getRowCount());

        builder.clearRows();
        assertEquals(0, builder.getRowCount());
        String md = builder.toMarkdown();
        // 헤더와 구분선만 존재
        assertTrue(md.contains("| Col  |"));
        assertTrue(md.contains("| :--- |"));
    }
}
