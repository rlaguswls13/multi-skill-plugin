package com.msp.gittag;

public record ParsedCommit(
        String hash,
        CommitType type,
        String scope,
        String description,
        boolean isBreaking
) {}
