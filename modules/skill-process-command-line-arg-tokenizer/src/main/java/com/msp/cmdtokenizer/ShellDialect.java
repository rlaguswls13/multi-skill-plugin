package com.msp.cmdtokenizer;

import java.util.Locale;

/**
 * Target operating system / shell command-line quoting dialect.
 */
public enum ShellDialect {
    /** POSIX sh/bash quoting rules (single quotes, double quotes, backslash escapes). */
    POSIX,
    /** Windows MSVC {@code CommandLineToArgvW} quoting rules. */
    WINDOWS;

    /**
     * Detects the current host OS dialect from the {@code os.name} system property.
     */
    public static ShellDialect currentOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? WINDOWS : POSIX;
    }
}
