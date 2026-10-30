package com.msp.gittag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitTagCalculatorTest {

    @Test
    @DisplayName("Parse Conventional Commit messages")
    void testParseCommits() {
        ParsedCommit c1 = GitTagCalculator.parseCommitMessage("a1b2c3d", "feat(auth): add JWT refresh token endpoint");
        assertEquals(CommitType.FEAT, c1.type());
        assertEquals("auth", c1.scope());
        assertEquals("add JWT refresh token endpoint", c1.description());
        assertFalse(c1.isBreaking());

        ParsedCommit c2 = GitTagCalculator.parseCommitMessage("e4f5g6h", "fix: correct date timezone offset");
        assertEquals(CommitType.FIX, c2.type());
        assertNull(c2.scope());
        assertFalse(c2.isBreaking());

        ParsedCommit c3 = GitTagCalculator.parseCommitMessage("i7j8k9l", "feat(api)!: remove deprecated v1 api\n\nBREAKING CHANGE: v1 api dropped");
        assertTrue(c3.isBreaking());
    }

    @Test
    @DisplayName("Calculate next SemVer version")
    void testCalculateNextVersion() {
        SemVer current = SemVer.of(1, 2, 0);

        // Fix -> patch bump
        ParsedCommit fixCommit = GitTagCalculator.parseCommitMessage("111", "fix: minor bug");
        assertEquals(SemVer.of(1, 2, 1), GitTagCalculator.calculateNextVersion(current, List.of(fixCommit)));

        // Feat -> minor bump
        ParsedCommit featCommit = GitTagCalculator.parseCommitMessage("222", "feat: new feature");
        assertEquals(SemVer.of(1, 3, 0), GitTagCalculator.calculateNextVersion(current, List.of(fixCommit, featCommit)));

        // Breaking -> major bump
        ParsedCommit breakingCommit = GitTagCalculator.parseCommitMessage("333", "feat!: breaking feature");
        assertEquals(SemVer.of(2, 0, 0), GitTagCalculator.calculateNextVersion(current, List.of(fixCommit, featCommit, breakingCommit)));
    }

    @Test
    @DisplayName("Generate markdown Changelog")
    void testChangelog() {
        List<ParsedCommit> commits = List.of(
                GitTagCalculator.parseCommitMessage("a1b2", "feat(core): new engine"),
                GitTagCalculator.parseCommitMessage("c3d4", "fix: memory leak on shutdown")
        );
        String changelog = GitTagCalculator.generateChangelog(SemVer.of(1, 1, 0), commits);
        assertTrue(changelog.contains("## v1.1.0"));
        assertTrue(changelog.contains("### 🚀 Features"));
        assertTrue(changelog.contains("- **core**: new engine (a1b2)"));
        assertTrue(changelog.contains("### 🐛 Bug Fixes"));
    }
}
