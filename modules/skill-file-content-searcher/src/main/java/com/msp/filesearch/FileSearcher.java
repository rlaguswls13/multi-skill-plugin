package com.msp.filesearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recursive multi-file text and regex grep utility with line numbers and match context.
 * Memory-safe line-by-line streaming, automatic binary file skip, zero external dependencies.
 */
public final class FileSearcher {

    private FileSearcher() {
    }

    /**
     * Searches for a literal text pattern recursively in files under rootDir.
     */
    public static List<SearchResult> search(Path rootDir, String text) throws IOException {
        return search(rootDir, SearchQuery.literal(text));
    }

    /**
     * Searches for a literal text pattern filtered by file glob.
     */
    public static List<SearchResult> search(Path rootDir, String text, String fileGlob) throws IOException {
        return search(rootDir, SearchQuery.builder().pattern(text).fileGlob(fileGlob).build());
    }

    /**
     * Searches for matches according to the specified query criteria.
     *
     * @param rootDir root directory to search
     * @param query   search query options
     * @return list of SearchResults
     * @throws IOException if rootDir cannot be accessed
     */
    public static List<SearchResult> search(Path rootDir, SearchQuery query) throws IOException {
        Objects.requireNonNull(rootDir, "rootDir must not be null");
        Objects.requireNonNull(query, "query must not be null");

        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            throw new IllegalArgumentException("Root path must be an existing directory: " + rootDir);
        }

        int regexFlags = query.caseSensitive() ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern compiledPattern = query.isRegex()
                ? Pattern.compile(query.pattern(), regexFlags)
                : Pattern.compile(Pattern.quote(query.pattern()), regexFlags);

        PathMatcher globMatcher = FileSystems.getDefault().getPathMatcher("glob:" + query.fileGlob());
        List<SearchResult> results = new ArrayList<>();

        Files.walkFileTree(rootDir, Collections.emptySet(), query.maxDepth(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (results.size() >= query.maxResults()) {
                    return FileVisitResult.TERMINATE;
                }

                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }

                Path fileName = file.getFileName();
                if (fileName == null || !globMatcher.matches(fileName)) {
                    return FileVisitResult.CONTINUE;
                }

                if (query.skipBinaryFiles() && isBinaryFile(file)) {
                    return FileVisitResult.CONTINUE;
                }

                searchInFile(file, compiledPattern, query.maxResults(), results);

                if (results.size() >= query.maxResults()) {
                    return FileVisitResult.TERMINATE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Safely skip unreadable files/directories
                return FileVisitResult.CONTINUE;
            }
        });

        return Collections.unmodifiableList(results);
    }

    private static void searchInFile(Path file, Pattern pattern, int maxResults, List<SearchResult> results) {
        try (InputStream is = Files.newInputStream(file);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8.newDecoder()
                     .onMalformedInput(CodingErrorAction.REPLACE)
                     .onUnmappableCharacter(CodingErrorAction.REPLACE));
             BufferedReader reader = new BufferedReader(isr)) {

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    results.add(new SearchResult(file, lineNumber, line, matcher.start(), matcher.end()));
                    if (results.size() >= maxResults) {
                        return;
                    }
                }
                lineNumber++;
            }
        } catch (IOException ignored) {
            // Ignore unreadable files
        }
    }

    /**
     * Inspects the first 512 bytes of a file. If a 0x00 (null byte) is found,
     * it is classified as a binary file.
     */
    public static boolean isBinaryFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[512];
            int read = in.read(buffer);
            for (int i = 0; i < read; i++) {
                if (buffer[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
