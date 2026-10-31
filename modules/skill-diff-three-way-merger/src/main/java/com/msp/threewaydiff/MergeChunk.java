package com.msp.threewaydiff;

import java.util.List;
import java.util.Objects;

/**
 * Represents a segmented chunk within a three-way merge operation.
 *
 * @param isConflict    true if both branches introduced conflicting changes
 * @param baseLines     original lines from the base ancestor for this chunk
 * @param oursLines     lines from the current branch (ours) for this chunk
 * @param theirsLines   lines from the incoming branch (theirs) for this chunk
 * @param resolvedLines merged lines if clean, or conflict marker lines if conflict
 */
public record MergeChunk(
        boolean isConflict,
        List<String> baseLines,
        List<String> oursLines,
        List<String> theirsLines,
        List<String> resolvedLines
) {
    public MergeChunk {
        baseLines = baseLines == null ? List.of() : List.copyOf(baseLines);
        oursLines = oursLines == null ? List.of() : List.copyOf(oursLines);
        theirsLines = theirsLines == null ? List.of() : List.copyOf(theirsLines);
        resolvedLines = resolvedLines == null ? List.of() : List.copyOf(resolvedLines);
    }

    /**
     * Creates an uncontroversial, clean merge chunk.
     */
    public static MergeChunk clean(List<String> base, List<String> ours, List<String> theirs, List<String> resolved) {
        return new MergeChunk(false, base, ours, theirs, resolved);
    }

    /**
     * Creates a conflict merge chunk formatted with Git conflict markers.
     */
    public static MergeChunk conflict(List<String> base, List<String> ours, List<String> theirs) {
        var resolved = new java.util.ArrayList<String>();
        resolved.add("<<<<<<< OURS");
        if (ours != null) {
            resolved.addAll(ours);
        }
        resolved.add("=======");
        if (theirs != null) {
            resolved.addAll(theirs);
        }
        resolved.add(">>>>>>> THEIRS");
        return new MergeChunk(true, base, ours, theirs, resolved);
    }
}
