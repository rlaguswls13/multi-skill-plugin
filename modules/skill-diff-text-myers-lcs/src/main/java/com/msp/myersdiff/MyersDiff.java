package com.msp.myersdiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Eugene Myers O(ND) Shortest Edit Script (SES) and Longest Common Subsequence (LCS) diff engine.
 * Strictly zero 3rd-party runtime dependencies (Pure Java 21).
 */
public final class MyersDiff {

    private MyersDiff() {
        // Static utility class
    }

    /**
     * Computes the line-by-line diff between original and revised text lines using Myers' SES algorithm.
     *
     * @param original list of lines in original file
     * @param revised  list of lines in revised file
     * @return list of DiffItem elements representing the minimal edit script
     */
    public static List<DiffItem> computeDiff(List<String> original, List<String> revised) {
        List<String> orig = original == null ? List.of() : original;
        List<String> rev = revised == null ? List.of() : revised;

        int n = orig.size();
        int m = rev.size();

        if (n == 0 && m == 0) {
            return List.of();
        }

        if (n == 0) {
            List<DiffItem> result = new ArrayList<>(m);
            for (int i = 0; i < m; i++) {
                result.add(new DiffItem(DiffOperationType.INSERT, rev.get(i), -1, i + 1));
            }
            return Collections.unmodifiableList(result);
        }

        if (m == 0) {
            List<DiffItem> result = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                result.add(new DiffItem(DiffOperationType.DELETE, orig.get(i), i + 1, -1));
            }
            return Collections.unmodifiableList(result);
        }

        int max = n + m;
        int[] v = new int[2 * max + 1];
        v[1 + max] = 0;

        List<int[]> trace = new ArrayList<>();
        boolean reachedEnd = false;

        for (int d = 0; d <= max; d++) {
            int[] vCopy = v.clone();
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && vCopy[k - 1 + max] < vCopy[k + 1 + max])) {
                    x = vCopy[k + 1 + max]; // Down: INSERT
                } else {
                    x = vCopy[k - 1 + max] + 1; // Right: DELETE
                }
                int y = x - k;

                // Follow diagonal snake
                while (x < n && y < m && Objects.equals(orig.get(x), rev.get(y))) {
                    x++;
                    y++;
                }

                v[k + max] = x;
                if (x >= n && y >= m) {
                    trace.add(v.clone());
                    reachedEnd = true;
                    break;
                }
            }
            if (!reachedEnd) {
                trace.add(v.clone());
            } else {
                break;
            }
        }

        // Backtrack to find the shortest edit path
        record Step(DiffOperationType type, int origIdx, int revIdx) {}
        List<Step> steps = new ArrayList<>();

        int currX = n;
        int currY = m;

        for (int d = trace.size() - 1; d > 0; d--) {
            int[] vPrev = trace.get(d - 1);
            int k = currX - currY;
            boolean cameFromDown = (k == -d || (k != d && vPrev[k - 1 + max] < vPrev[k + 1 + max]));
            int prevK = cameFromDown ? (k + 1) : (k - 1);
            int prevX = vPrev[prevK + max];
            int prevY = prevX - prevK;

            int midX = cameFromDown ? prevX : prevX + 1;
            int midY = cameFromDown ? prevY + 1 : prevY;

            while (currX > midX && currY > midY) {
                steps.add(new Step(DiffOperationType.KEEP, currX - 1, currY - 1));
                currX--;
                currY--;
            }

            if (cameFromDown) {
                steps.add(new Step(DiffOperationType.INSERT, -1, prevY));
            } else {
                steps.add(new Step(DiffOperationType.DELETE, prevX, -1));
            }

            currX = prevX;
            currY = prevY;
        }

        while (currX > 0 && currY > 0) {
            steps.add(new Step(DiffOperationType.KEEP, currX - 1, currY - 1));
            currX--;
            currY--;
        }

        Collections.reverse(steps);

        // Convert steps into DiffItems with 1-based line numbering
        List<DiffItem> items = new ArrayList<>(steps.size());
        int oldLine = 1;
        int newLine = 1;

        for (Step s : steps) {
            switch (s.type) {
                case KEEP -> {
                    items.add(new DiffItem(DiffOperationType.KEEP, orig.get(s.origIdx), oldLine, newLine));
                    oldLine++;
                    newLine++;
                }
                case DELETE -> {
                    items.add(new DiffItem(DiffOperationType.DELETE, orig.get(s.origIdx), oldLine, -1));
                    oldLine++;
                }
                case INSERT -> {
                    items.add(new DiffItem(DiffOperationType.INSERT, rev.get(s.revIdx), -1, newLine));
                    newLine++;
                }
            }
        }

        return Collections.unmodifiableList(items);
    }

    /**
     * Generates a standard Unified Diff formatted string.
     *
     * @param originalName name or path of the original file
     * @param revisedName  name or path of the revised file
     * @param original     original lines
     * @param revised      revised lines
     * @param contextLines number of context lines around differences (>= 0)
     * @return unified diff string (empty string if files are identical)
     */
    public static String toUnifiedDiff(String originalName,
                                      String revisedName,
                                      List<String> original,
                                      List<String> revised,
                                      int contextLines) {
        List<String> orig = original == null ? List.of() : original;
        List<String> rev = revised == null ? List.of() : revised;

        if (orig.equals(rev)) {
            return "";
        }

        int ctx = Math.max(0, contextLines);
        List<DiffItem> diff = computeDiff(orig, rev);

        boolean hasChanges = false;
        for (DiffItem item : diff) {
            if (item.type() != DiffOperationType.KEEP) {
                hasChanges = true;
                break;
            }
        }
        if (!hasChanges) {
            return "";
        }

        record Range(int start, int end) {}
        List<Range> hunks = new ArrayList<>();
        Range currentHunk = null;

        for (int i = 0; i < diff.size(); i++) {
            if (diff.get(i).type() != DiffOperationType.KEEP) {
                int rStart = Math.max(0, i - ctx);
                int rEnd = Math.min(diff.size() - 1, i + ctx);
                if (currentHunk == null) {
                    currentHunk = new Range(rStart, rEnd);
                } else if (rStart <= currentHunk.end() + 1) {
                    // Merge overlapping or adjacent hunks
                    currentHunk = new Range(currentHunk.start(), Math.max(currentHunk.end(), rEnd));
                } else {
                    hunks.add(currentHunk);
                    currentHunk = new Range(rStart, rEnd);
                }
            }
        }
        if (currentHunk != null) {
            hunks.add(currentHunk);
        }

        StringBuilder sb = new StringBuilder();
        String oName = (originalName == null || originalName.isBlank()) ? "a" : originalName;
        String rName = (revisedName == null || revisedName.isBlank()) ? "b" : revisedName;

        sb.append("--- ").append(oName).append('\n');
        sb.append("+++ ").append(rName).append('\n');

        for (Range hunk : hunks) {
            int oldStart = 0;
            int oldCount = 0;
            int newStart = 0;
            int newCount = 0;

            for (int i = hunk.start(); i <= hunk.end(); i++) {
                DiffItem item = diff.get(i);
                if (item.oldLineNum() > 0 && oldStart == 0) {
                    oldStart = item.oldLineNum();
                }
                if (item.newLineNum() > 0 && newStart == 0) {
                    newStart = item.newLineNum();
                }

                switch (item.type()) {
                    case KEEP -> {
                        oldCount++;
                        newCount++;
                    }
                    case DELETE -> oldCount++;
                    case INSERT -> newCount++;
                }
            }

            // Fallback for empty original or revised hunks
            if (oldStart == 0) {
                for (int i = hunk.start() - 1; i >= 0; i--) {
                    if (diff.get(i).oldLineNum() > 0) {
                        oldStart = diff.get(i).oldLineNum();
                        break;
                    }
                }
            }
            if (newStart == 0) {
                for (int i = hunk.start() - 1; i >= 0; i--) {
                    if (diff.get(i).newLineNum() > 0) {
                        newStart = diff.get(i).newLineNum();
                        break;
                    }
                }
            }

            sb.append("@@ -").append(oldStart).append(',').append(oldCount)
              .append(" +").append(newStart).append(',').append(newCount)
              .append(" @@\n");

            for (int i = hunk.start(); i <= hunk.end(); i++) {
                DiffItem item = diff.get(i);
                switch (item.type()) {
                    case KEEP -> sb.append(' ').append(item.text()).append('\n');
                    case DELETE -> sb.append('-').append(item.text()).append('\n');
                    case INSERT -> sb.append('+').append(item.text()).append('\n');
                }
            }
        }

        return sb.toString();
    }
}
