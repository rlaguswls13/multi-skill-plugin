package com.msp.filesearch;

import java.util.Objects;

/**
 * Query criteria and options for FileSearcher.
 *
 * @param pattern          text pattern or regular expression to search for
 * @param isRegex          whether pattern should be treated as a regular expression
 * @param caseSensitive    whether search should be case sensitive (default true)
 * @param maxDepth         maximum directory depth for recursive traversal (default Integer.MAX_VALUE)
 * @param fileGlob         glob pattern for file names to include (default "*")
 * @param maxResults       maximum number of matches to return (default Integer.MAX_VALUE)
 * @param skipBinaryFiles  whether to automatically detect and skip binary files (default true)
 */
public record SearchQuery(
        String pattern,
        boolean isRegex,
        boolean caseSensitive,
        int maxDepth,
        String fileGlob,
        int maxResults,
        boolean skipBinaryFiles
) {
    public SearchQuery {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(fileGlob, "fileGlob must not be null");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("Search pattern must not be empty");
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be non-negative");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be greater than 0");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SearchQuery literal(String text) {
        return builder().pattern(text).isRegex(false).build();
    }

    public static SearchQuery regex(String regex) {
        return builder().pattern(regex).isRegex(true).build();
    }

    public static final class Builder {
        private String pattern;
        private boolean isRegex = false;
        private boolean caseSensitive = true;
        private int maxDepth = Integer.MAX_VALUE;
        private String fileGlob = "*";
        private int maxResults = Integer.MAX_VALUE;
        private boolean skipBinaryFiles = true;

        private Builder() {
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder isRegex(boolean isRegex) {
            this.isRegex = isRegex;
            return this;
        }

        public Builder caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder fileGlob(String fileGlob) {
            this.fileGlob = fileGlob;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder skipBinaryFiles(boolean skipBinaryFiles) {
            this.skipBinaryFiles = skipBinaryFiles;
            return this;
        }

        public SearchQuery build() {
            return new SearchQuery(
                    pattern,
                    isRegex,
                    caseSensitive,
                    maxDepth,
                    fileGlob,
                    maxResults,
                    skipBinaryFiles
            );
        }
    }
}
