package com.msp.ptyterm;

import java.util.regex.Pattern;

/**
 * Filter for stripping ANSI/VT100 escape sequences from CLI outputs.
 */
public final class AnsiEscapeFilter {

    // Matches ANSI CSI sequences: \u001B[ ... [a-zA-Z]
    private static final Pattern ANSI_CSI_PATTERN = Pattern.compile("\\u001B\\[[;?0-9]*[a-zA-Z]");

    // Matches OSC (Operating System Command) sequences: \u001B] ... (\u0007|\u001B\\)
    private static final Pattern ANSI_OSC_PATTERN = Pattern.compile("\\u001B\\][^\u0007\u001B]*(\\u0007|\\u001B\\\\)");

    // Matches 2-character escape sequences like charset designation or keypad mode
    private static final Pattern ANSI_ESC_PATTERN = Pattern.compile("\\u001B[()#%][A-Za-z0-9]|\\u001B[=>]");

    private AnsiEscapeFilter() {}

    /**
     * Removes all ANSI color, formatting, and cursor control escape sequences from the string.
     */
    public static String stripAnsi(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String stripped = ANSI_OSC_PATTERN.matcher(text).replaceAll("");
        stripped = ANSI_CSI_PATTERN.matcher(stripped).replaceAll("");
        stripped = ANSI_ESC_PATTERN.matcher(stripped).replaceAll("");
        return stripped;
    }
}
