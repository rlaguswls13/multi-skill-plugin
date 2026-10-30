package com.msp.gittag;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conventional Commits 명세를 기반으로
 * 차기 SemVer 버전(Major/Minor/Patch) 자동 산출 및 변경 로그(Changelog)를 생성하는 태거.
 */
public final class GitTagCalculator {

    private static final Pattern COMMIT_PATTERN = Pattern.compile("^([a-zA-Z]+)(?:\\(([^)]+)\\))?(!)?\\s*:\\s*(.+)");

    private GitTagCalculator() {}

    public static ParsedCommit parseCommitMessage(String hash, String message) {
        if (message == null || message.isBlank()) {
            return new ParsedCommit(hash, CommitType.OTHER, null, "", false);
        }

        String firstLine = message.trim().split("\r?\n")[0];
        Matcher m = COMMIT_PATTERN.matcher(firstLine);

        boolean isBreaking = message.contains("BREAKING CHANGE:") || message.contains("BREAKING-CHANGE:");

        if (m.find()) {
            String typeStr = m.group(1).toUpperCase(Locale.ROOT);
            String scope = m.group(2);
            boolean exclamation = m.group(3) != null;
            String desc = m.group(4);

            if (exclamation) {
                isBreaking = true;
            }

            CommitType type = switch (typeStr) {
                case "FEAT" -> CommitType.FEAT;
                case "FIX" -> CommitType.FIX;
                case "DOCS" -> CommitType.DOCS;
                case "CHORE" -> CommitType.CHORE;
                case "REFACTOR" -> CommitType.REFACTOR;
                case "PERF" -> CommitType.PERF;
                case "TEST" -> CommitType.TEST;
                default -> CommitType.OTHER;
            };

            return new ParsedCommit(hash, type, scope, desc, isBreaking);
        }

        return new ParsedCommit(hash, CommitType.OTHER, null, firstLine, isBreaking);
    }

    public static SemVer calculateNextVersion(SemVer currentVersion, List<ParsedCommit> commits) {
        Objects.requireNonNull(currentVersion, "currentVersion must not be null");
        if (commits == null || commits.isEmpty()) {
            return currentVersion;
        }

        boolean hasBreaking = false;
        boolean hasFeat = false;
        boolean hasFix = false;

        for (ParsedCommit c : commits) {
            if (c.isBreaking()) {
                hasBreaking = true;
            } else if (c.type() == CommitType.FEAT) {
                hasFeat = true;
            } else if (c.type() == CommitType.FIX || c.type() == CommitType.PERF || c.type() == CommitType.REFACTOR) {
                hasFix = true;
            }
        }

        if (hasBreaking) {
            return currentVersion.bumpMajor();
        }
        if (hasFeat) {
            return currentVersion.bumpMinor();
        }
        if (hasFix) {
            return currentVersion.bumpPatch();
        }
        return currentVersion;
    }

    public static String generateChangelog(SemVer version, List<ParsedCommit> commits) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(version.format(true)).append("\n\n");

        List<ParsedCommit> breakings = new ArrayList<>();
        List<ParsedCommit> feats = new ArrayList<>();
        List<ParsedCommit> fixes = new ArrayList<>();

        for (ParsedCommit c : commits) {
            if (c.isBreaking()) breakings.add(c);
            else if (c.type() == CommitType.FEAT) feats.add(c);
            else if (c.type() == CommitType.FIX) fixes.add(c);
        }

        if (!breakings.isEmpty()) {
            sb.append("### ⚠️ Breaking Changes\n");
            for (ParsedCommit c : breakings) {
                sb.append("- ").append(c.description()).append(" (").append(c.hash()).append(")\n");
            }
            sb.append("\n");
        }

        if (!feats.isEmpty()) {
            sb.append("### 🚀 Features\n");
            for (ParsedCommit c : feats) {
                sb.append("- ").append(c.scope() != null ? "**" + c.scope() + "**: " : "").append(c.description()).append(" (").append(c.hash()).append(")\n");
            }
            sb.append("\n");
        }

        if (!fixes.isEmpty()) {
            sb.append("### 🐛 Bug Fixes\n");
            for (ParsedCommit c : fixes) {
                sb.append("- ").append(c.scope() != null ? "**" + c.scope() + "**: " : "").append(c.description()).append(" (").append(c.hash()).append(")\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
