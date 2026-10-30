package com.msp.diffview;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Report containing all evaluated differences between two datasets.
 */
public record DiffReport(List<DiffEntry> entries) {

    public DiffReport {
        Objects.requireNonNull(entries, "entries must not be null");
        entries = List.copyOf(entries);
    }

    public boolean hasChanges() {
        return entries.stream().anyMatch(DiffEntry::hasChange);
    }

    public List<DiffEntry> getAdded() {
        return entries.stream().filter(e -> e.type() == DiffType.ADDED).toList();
    }

    public List<DiffEntry> getRemoved() {
        return entries.stream().filter(e -> e.type() == DiffType.REMOVED).toList();
    }

    public List<DiffEntry> getModified() {
        return entries.stream().filter(e -> e.type() == DiffType.MODIFIED).toList();
    }

    public List<DiffEntry> getUnchanged() {
        return entries.stream().filter(e -> e.type() == DiffType.UNCHANGED).toList();
    }

    public String toSummaryString() {
        return String.format("+%d -%d ~%d (unchanged: %d)",
                getAdded().size(),
                getRemoved().size(),
                getModified().size(),
                getUnchanged().size()
        );
    }

    /**
     * Formats changed entries into a GitHub-flavored Markdown table.
     */
    public String toMarkdownTable() {
        List<DiffEntry> changes = entries.stream().filter(DiffEntry::hasChange).toList();
        if (changes.isEmpty()) {
            return "_No differences detected._";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("| Field Path | Diff Type | Before | After |\n");
        sb.append("|---|---|---|---|\n");

        for (DiffEntry e : changes) {
            sb.append("| `").append(e.path()).append("` | ")
                    .append(badge(e.type())).append(" | ")
                    .append(formatValue(e.oldValue())).append(" | ")
                    .append(formatValue(e.newValue())).append(" |\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String badge(DiffType type) {
        return switch (type) {
            case ADDED -> "🟢 ADDED";
            case REMOVED -> "🔴 REMOVED";
            case MODIFIED -> "🟡 MODIFIED";
            case UNCHANGED -> "⚪ UNCHANGED";
        };
    }

    private static String formatValue(Object val) {
        if (val == null) return "`-`";
        return "`" + val.toString().replace("`", "'") + "`";
    }
}
