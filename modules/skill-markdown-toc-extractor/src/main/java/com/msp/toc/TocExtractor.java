package com.msp.toc;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts hierarchical Table of Contents (TOC) and generates GitHub-compatible anchor slugs from Markdown text.
 */
public final class TocExtractor {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*$", Pattern.MULTILINE);

    private TocExtractor() {}

    /**
     * Extracts all heading items from markdown, ignoring headings inside code fences.
     */
    public static List<TocItem> extract(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Collections.emptyList();
        }

        List<TocItem> items = new ArrayList<>();
        Map<String, Integer> slugCounts = new HashMap<>();

        // Process line by line to accurately handle code fences
        String[] lines = markdown.split("\\R");
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                continue;
            }

            Matcher matcher = HEADING_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                int level = matcher.group(1).length();
                String rawTitle = matcher.group(2).trim();
                String cleanTitle = stripMarkdownFormatting(rawTitle);
                String baseSlug = generateSlug(cleanTitle);

                // Handle duplicates
                int count = slugCounts.getOrDefault(baseSlug, 0);
                String uniqueSlug = count == 0 ? baseSlug : baseSlug + "-" + count;
                slugCounts.put(baseSlug, count + 1);

                items.add(new TocItem(level, cleanTitle, uniqueSlug));
            }
        }

        return Collections.unmodifiableList(items);
    }

    /**
     * Generates a full hierarchical Table of Contents in Markdown list format.
     */
    public static String generateTocMarkdown(String markdown) {
        List<TocItem> items = extract(markdown);
        if (items.isEmpty()) {
            return "";
        }

        int minLevel = items.stream().mapToInt(TocItem::level).min().orElse(1);
        StringBuilder sb = new StringBuilder();
        for (TocItem item : items) {
            sb.append(item.toMarkdown(minLevel)).append(System.lineSeparator());
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Generates a GitHub-compatible URL slug anchor from a title.
     */
    public static String generateSlug(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        // Lowercase
        String slug = title.toLowerCase(Locale.ROOT);
        // Remove punctuation and special characters, keep letters, numbers, hyphens, and unicode letters (e.g. Korean)
        slug = slug.replaceAll("[^\\p{L}\\p{Nd}\\s-]", "");
        // Replace spaces with hyphens
        slug = slug.trim().replaceAll("\\s+", "-");
        return slug;
    }

    private static String stripMarkdownFormatting(String text) {
        // Remove bold/italics (* and _)
        String clean = text.replaceAll("[*_~`]", "");
        // Remove links [text](url) -> text
        clean = clean.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        return clean.trim();
    }
}
