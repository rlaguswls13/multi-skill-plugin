package com.msp.filesearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileSearcherTest {

    @Test
    @DisplayName("Should find literal string matches with accurate line numbers and offsets")
    void shouldFindLiteralMatches(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("sample1.txt");
        Files.writeString(file1, "First line\nSecond line with keyword here\nThird line\n");

        Path file2 = tempDir.resolve("sample2.txt");
        Files.writeString(file2, "Header\nAnother keyword occurrence\nFooter\n");

        List<SearchResult> results = FileSearcher.search(tempDir, "keyword");

        assertEquals(2, results.size());

        SearchResult r1 = results.get(0);
        assertEquals(file1, r1.path());
        assertEquals(2, r1.lineNumber());
        assertEquals("Second line with keyword here", r1.lineContent());
        assertEquals("keyword", r1.matchedText());

        SearchResult r2 = results.get(1);
        assertEquals(file2, r2.path());
        assertEquals(2, r2.lineNumber());
        assertEquals("Another keyword occurrence", r2.lineContent());
        assertEquals("keyword", r2.matchedText());
    }

    @Test
    @DisplayName("Should find regex matches with groups or patterns")
    void shouldFindRegexMatches(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("users.txt");
        Files.writeString(file, "user_101 connected\nadmin disconnected\nuser_202 connected\n");

        SearchQuery query = SearchQuery.builder()
                .pattern("user_\\d+")
                .isRegex(true)
                .build();

        List<SearchResult> results = FileSearcher.search(tempDir, query);

        assertEquals(2, results.size());
        assertEquals("user_101", results.get(0).matchedText());
        assertEquals(1, results.get(0).lineNumber());

        assertEquals("user_202", results.get(1).matchedText());
        assertEquals(3, results.get(1).lineNumber());
    }

    @Test
    @DisplayName("Should filter files by fileGlob")
    void shouldFilterByFileGlob(@TempDir Path tempDir) throws IOException {
        Path txtFile = tempDir.resolve("data.txt");
        Path logFile = tempDir.resolve("data.log");

        Files.writeString(txtFile, "target in txt");
        Files.writeString(logFile, "target in log");

        List<SearchResult> results = FileSearcher.search(tempDir, "target", "*.txt");

        assertEquals(1, results.size());
        assertEquals(txtFile, results.get(0).path());
    }

    @Test
    @DisplayName("Should automatically skip binary files containing null bytes")
    void shouldSkipBinaryFiles(@TempDir Path tempDir) throws IOException {
        Path binFile = tempDir.resolve("app.bin");
        // write text with a null byte in the middle
        byte[] bytes = new byte[]{'t', 'a', 'r', 'g', 'e', 't', 0x00, 'm', 'o', 'r', 'e'};
        Files.write(binFile, bytes);

        Path textFile = tempDir.resolve("app.txt");
        Files.writeString(textFile, "target in valid text file");

        List<SearchResult> results = FileSearcher.search(tempDir, "target");

        assertEquals(1, results.size());
        assertEquals(textFile, results.get(0).path());
    }

    @Test
    @DisplayName("Should respect maxResults limit across files")
    void shouldRespectMaxResults(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("multi.txt");
        Files.writeString(file, "match 1\nmatch 2\nmatch 3\nmatch 4\n");

        SearchQuery query = SearchQuery.builder()
                .pattern("match")
                .maxResults(2)
                .build();

        List<SearchResult> results = FileSearcher.search(tempDir, query);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("Should support case-insensitive search")
    void shouldSupportCaseInsensitiveSearch(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("case.txt");
        Files.writeString(file, "Apple, aPple, APPLE, banana");

        SearchQuery query = SearchQuery.builder()
                .pattern("apple")
                .caseSensitive(false)
                .build();

        List<SearchResult> results = FileSearcher.search(tempDir, query);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("Should search recursively in nested subdirectories")
    void shouldSearchRecursively(@TempDir Path tempDir) throws IOException {
        Path nested = Files.createDirectories(tempDir.resolve("sub1/sub2"));
        Path file = nested.resolve("nested.txt");
        Files.writeString(file, "deep search hit");

        List<SearchResult> results = FileSearcher.search(tempDir, "deep search");
        assertEquals(1, results.size());
        assertEquals(file, results.get(0).path());
    }
}
