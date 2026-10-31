package com.msp.threewaydiff;

import java.util.List;
import java.util.Objects;

/**
 * Result of a three-way merge operation.
 *
 * @param hasConflicts    true if one or more merge conflicts occurred
 * @param mergedLines     final merged lines including conflict markers if any
 * @param formattedOutput merged lines joined with system line separators (or \n)
 */
public record MergeResult(
        boolean hasConflicts,
        List<String> mergedLines,
        String formattedOutput
) {
    public MergeResult {
        Objects.requireNonNull(formattedOutput, "formattedOutput cannot be null");
        mergedLines = mergedLines == null ? List.of() : List.copyOf(mergedLines);
    }

    public static MergeResult of(boolean hasConflicts, List<String> mergedLines) {
        String formatted = String.join("\n", mergedLines);
        return new MergeResult(hasConflicts, mergedLines, formatted);
    }
}
