package com.msp.threewaydiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure Java 21 Git-style three-way merge engine.
 * Computes non-overlapping hunks and detects conflicts with standard markers.
 */
public final class ThreeWayMerger {

    private ThreeWayMerger() {
        // Utility class
    }

    /**
     * Merges two branches (ours and theirs) against a common base ancestor string.
     *
     * @param baseText   common ancestor content
     * @param oursText   current branch content
     * @param theirsText incoming branch content
     * @return {@link MergeResult} containing conflict status, merged lines, and formatted text
     */
    public static MergeResult merge(String baseText, String oursText, String theirsText) {
        List<String> baseLines = splitLines(baseText);
        List<String> oursLines = splitLines(oursText);
        List<String> theirsLines = splitLines(theirsText);
        return merge(baseLines, oursLines, theirsLines);
    }

    /**
     * Merges two line lists (ours and theirs) against a common base ancestor line list.
     *
     * @param baseLines   common ancestor lines
     * @param oursLines   current branch lines
     * @param theirsLines incoming branch lines
     * @return {@link MergeResult} containing conflict status, merged lines, and formatted text
     */
    public static MergeResult merge(List<String> baseLines, List<String> oursLines, List<String> theirsLines) {
        List<MergeChunk> chunks = mergeChunks(baseLines, oursLines, theirsLines);
        boolean hasConflicts = false;
        List<String> mergedLines = new ArrayList<>();

        for (MergeChunk chunk : chunks) {
            if (chunk.isConflict()) {
                hasConflicts = true;
            }
            mergedLines.addAll(chunk.resolvedLines());
        }

        return MergeResult.of(hasConflicts, mergedLines);
    }

    /**
     * Produces the full list of structured {@link MergeChunk}s representing the 3-way merge.
     */
    public static List<MergeChunk> mergeChunks(List<String> baseLines, List<String> oursLines, List<String> theirsLines) {
        List<String> base = baseLines == null ? List.of() : List.copyOf(baseLines);
        List<String> ours = oursLines == null ? List.of() : List.copyOf(oursLines);
        List<String> theirs = theirsLines == null ? List.of() : List.copyOf(theirsLines);

        List<DiffHunk> editsO = computeDiffHunks(base, ours, Branch.OURS);
        List<DiffHunk> editsT = computeDiffHunks(base, theirs, Branch.THEIRS);

        List<Cluster> clusters = buildClusters(editsO, editsT);

        List<MergeChunk> result = new ArrayList<>();
        int currentBaseIdx = 0;

        for (Cluster cluster : clusters) {
            // 1. Untouched base lines before this cluster
            if (cluster.start > currentBaseIdx) {
                List<String> untouched = base.subList(currentBaseIdx, cluster.start);
                result.add(MergeChunk.clean(untouched, untouched, untouched, untouched));
            }

            // 2. Resolve cluster
            List<String> clusterBase = (cluster.start < cluster.end)
                    ? base.subList(cluster.start, cluster.end)
                    : List.of();

            List<String> sliceO = getBranchSlice(base, editsO, ours, cluster.start, cluster.end);
            List<String> sliceT = getBranchSlice(base, editsT, theirs, cluster.start, cluster.end);

            if (!cluster.hasOurs) {
                // Change only in theirs
                result.add(MergeChunk.clean(clusterBase, clusterBase, sliceT, sliceT));
            } else if (!cluster.hasTheirs) {
                // Change only in ours
                result.add(MergeChunk.clean(clusterBase, sliceO, clusterBase, sliceO));
            } else {
                // Both branches changed this region
                if (sliceO.equals(sliceT)) {
                    // Clean merge (identical changes)
                    result.add(MergeChunk.clean(clusterBase, sliceO, sliceT, sliceO));
                } else {
                    // Conflict!
                    result.add(MergeChunk.conflict(clusterBase, sliceO, sliceT));
                }
            }

            currentBaseIdx = cluster.end;
        }

        // 3. Untouched base lines after last cluster
        if (currentBaseIdx < base.size()) {
            List<String> untouched = base.subList(currentBaseIdx, base.size());
            result.add(MergeChunk.clean(untouched, untouched, untouched, untouched));
        }

        return Collections.unmodifiableList(result);
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(text.split("\\r?\\n", -1));
    }

    private enum Branch { OURS, THEIRS }

    private record DiffHunk(
            int baseStart,
            int baseEnd,
            List<String> replacement,
            Branch branch
    ) {}

    private static class Cluster {
        int start;
        int end;
        boolean hasOurs;
        boolean hasTheirs;

        Cluster(int start, int end, boolean hasOurs, boolean hasTheirs) {
            this.start = start;
            this.end = end;
            this.hasOurs = hasOurs;
            this.hasTheirs = hasTheirs;
        }
    }

    private static List<DiffHunk> computeDiffHunks(List<String> base, List<String> branch, Branch branchType) {
        List<int[]> lcs = computeLcsMatches(base, branch);
        List<DiffHunk> hunks = new ArrayList<>();

        int prevB = 0;
        int prevY = 0;

        for (int[] match : lcs) {
            int bIdx = match[0];
            int yIdx = match[1];

            if (prevB < bIdx || prevY < yIdx) {
                List<String> repl = (prevY < yIdx)
                        ? branch.subList(prevY, yIdx)
                        : List.of();
                hunks.add(new DiffHunk(prevB, bIdx, repl, branchType));
            }

            prevB = bIdx + 1;
            prevY = yIdx + 1;
        }

        if (prevB < base.size() || prevY < branch.size()) {
            List<String> repl = (prevY < branch.size())
                    ? branch.subList(prevY, branch.size())
                    : List.of();
            hunks.add(new DiffHunk(prevB, base.size(), repl, branchType));
        }

        return hunks;
    }

    private static List<int[]> computeLcsMatches(List<String> a, List<String> b) {
        int m = a.size();
        int n = b.size();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (Objects.equals(a.get(i), b.get(j))) {
                    dp[i + 1][j + 1] = dp[i][j] + 1;
                } else {
                    dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<int[]> matches = new ArrayList<>();
        int i = m;
        int j = n;
        while (i > 0 && j > 0) {
            if (Objects.equals(a.get(i - 1), b.get(j - 1))) {
                matches.add(new int[]{i - 1, j - 1});
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }

        Collections.reverse(matches);
        return matches;
    }

    private static List<Cluster> buildClusters(List<DiffHunk> editsO, List<DiffHunk> editsT) {
        List<DiffHunk> allEdits = new ArrayList<>();
        allEdits.addAll(editsO);
        allEdits.addAll(editsT);

        allEdits.sort(Comparator.comparingInt(DiffHunk::baseStart)
                .thenComparingInt(DiffHunk::baseEnd));

        List<Cluster> clusters = new ArrayList<>();
        for (DiffHunk hunk : allEdits) {
            boolean merged = false;
            if (!clusters.isEmpty()) {
                Cluster last = clusters.getLast();
                if (isOverlapping(last, hunk)) {
                    last.start = Math.min(last.start, hunk.baseStart);
                    last.end = Math.max(last.end, hunk.baseEnd);
                    if (hunk.branch == Branch.OURS) {
                        last.hasOurs = true;
                    } else {
                        last.hasTheirs = true;
                    }
                    merged = true;
                }
            }
            if (!merged) {
                clusters.add(new Cluster(
                        hunk.baseStart,
                        hunk.baseEnd,
                        hunk.branch == Branch.OURS,
                        hunk.branch == Branch.THEIRS
                ));
            }
        }
        return clusters;
    }

    private static boolean isOverlapping(Cluster cluster, DiffHunk hunk) {
        boolean clusterIsInsert = (cluster.start == cluster.end);
        boolean hunkIsInsert = (hunk.baseStart == hunk.baseEnd);

        if (clusterIsInsert && hunkIsInsert) {
            return cluster.start == hunk.baseStart;
        }
        if (clusterIsInsert) {
            return hunk.baseStart < cluster.start && cluster.start < hunk.baseEnd;
        }
        if (hunkIsInsert) {
            return cluster.start < hunk.baseStart && hunk.baseStart < cluster.end;
        }
        // Both are ranges: overlap only if intersection is non-empty
        return Math.max(cluster.start, hunk.baseStart) < Math.min(cluster.end, hunk.baseEnd);
    }

    private static List<String> getBranchSlice(List<String> base, List<DiffHunk> branchHunks, List<String> branch, int bStart, int bEnd) {
        List<String> slice = new ArrayList<>();
        int curr = bStart;

        for (DiffHunk hunk : branchHunks) {
            if (hunk.baseEnd < bStart) {
                continue;
            }
            if (hunk.baseStart > bEnd) {
                break;
            }

            // Untouched base lines between curr and hunk.baseStart
            if (hunk.baseStart > curr) {
                slice.addAll(base.subList(curr, Math.min(hunk.baseStart, bEnd)));
            }

            // If hunk is within range
            if (hunk.baseStart >= bStart && hunk.baseEnd <= bEnd) {
                slice.addAll(hunk.replacement);
                curr = Math.max(curr, hunk.baseEnd);
            }
        }

        if (curr < bEnd) {
            slice.addAll(base.subList(curr, bEnd));
        }

        return slice;
    }
}
