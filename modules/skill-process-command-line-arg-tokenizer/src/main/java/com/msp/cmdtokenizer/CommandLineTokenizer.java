package com.msp.cmdtokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Thread-safe, cross-platform command-line argument tokenizer and injection-safe shell quoter.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@link ShellDialect#POSIX}: single quotes, double quotes, backslash escapes,
 *       line continuations, and adjacent segment concatenation</li>
 *   <li>{@link ShellDialect#WINDOWS}: MSVC {@code CommandLineToArgvW} backslash-before-quote
 *       and doubled-quote rules</li>
 * </ul>
 */
public final class CommandLineTokenizer {

    private static final Pattern POSIX_SAFE_BAREWORD =
            Pattern.compile("^[A-Za-z0-9_@%+=:,./-]+$");

    private final ShellDialect dialect;

    private CommandLineTokenizer(ShellDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
    }

    /**
     * Returns a tokenizer/formatter configured for {@code dialect}.
     */
    public static CommandLineTokenizer forDialect(ShellDialect dialect) {
        return new CommandLineTokenizer(dialect);
    }

    public ShellDialect dialect() {
        return dialect;
    }

    /**
     * Tokenizes a raw command-line string into individual argument tokens using this dialect.
     */
    public List<String> tokenize(String commandLine) {
        return tokenize(commandLine, dialect);
    }

    /**
     * Quotes a single argument safely for this dialect.
     */
    public String quote(String argument) {
        return quote(argument, dialect);
    }

    /**
     * Formats an ordered list of arguments into a safely escaped command-line string for this dialect.
     */
    public String format(List<String> arguments) {
        return format(arguments, dialect);
    }

    /**
     * Tokenizes a raw command-line string using the specified {@link ShellDialect}.
     */
    public static List<String> tokenize(String commandLine, ShellDialect dialect) {
        Objects.requireNonNull(commandLine, "commandLine must not be null");
        Objects.requireNonNull(dialect, "dialect must not be null");
        if (commandLine.indexOf('\0') >= 0) {
            throw new CommandLineParseException("Command line must not contain NUL characters");
        }
        return switch (dialect) {
            case POSIX -> tokenizePosix(commandLine);
            case WINDOWS -> tokenizeWindows(commandLine);
        };
    }

    /**
     * Safely quotes a single argument for the specified {@link ShellDialect}.
     */
    public static String quote(String argument, ShellDialect dialect) {
        Objects.requireNonNull(argument, "argument must not be null");
        Objects.requireNonNull(dialect, "dialect must not be null");
        if (argument.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Argument must not contain NUL characters");
        }
        return switch (dialect) {
            case POSIX -> quotePosix(argument);
            case WINDOWS -> quoteWindows(argument);
        };
    }

    /**
     * Formats an argument list into a single space-delimited, safely quoted command-line string.
     */
    public static String format(List<String> arguments, ShellDialect dialect) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(dialect, "dialect must not be null");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(quote(arguments.get(i), dialect));
        }
        return sb.toString();
    }

    private static List<String> tokenizePosix(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inToken = false;
        boolean inSingle = false;
        boolean inDouble = false;

        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                } else {
                    current.append(c);
                }
            } else if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                } else if (c == '\\') {
                    if (i + 1 >= len) {
                        throw new CommandLineParseException("Unterminated escape sequence inside double quotes");
                    }
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`') {
                        current.append(next);
                        i++;
                    } else if (next == '\n') {
                        i++; // Line continuation
                    } else {
                        current.append('\\');
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    if (inToken) {
                        tokens.add(current.toString());
                        current.setLength(0);
                        inToken = false;
                    }
                } else if (c == '\'') {
                    inSingle = true;
                    inToken = true;
                } else if (c == '"') {
                    inDouble = true;
                    inToken = true;
                } else if (c == '\\') {
                    if (i + 1 >= len) {
                        throw new CommandLineParseException("Trailing unescaped backslash at end of POSIX command line");
                    }
                    char next = input.charAt(i + 1);
                    if (next == '\n') {
                        i++; // Line continuation
                    } else {
                        current.append(next);
                        inToken = true;
                        i++;
                    }
                } else {
                    current.append(c);
                    inToken = true;
                }
            }
        }

        if (inSingle) {
            throw new CommandLineParseException("Unterminated single quote in POSIX command line");
        }
        if (inDouble) {
            throw new CommandLineParseException("Unterminated double quote in POSIX command line");
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return List.copyOf(tokens);
    }

    private static List<String> tokenizeWindows(String input) {
        List<String> tokens = new ArrayList<>();
        int len = input.length();
        int i = 0;

        while (i < len) {
            while (i < len && (input.charAt(i) == ' ' || input.charAt(i) == '\t')) {
                i++;
            }
            if (i >= len) {
                break;
            }

            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;

            while (i < len) {
                char c = input.charAt(i);
                if (!inQuotes && (c == ' ' || c == '\t')) {
                    break;
                }
                if (c == '\\') {
                    int bsCount = 0;
                    while (i < len && input.charAt(i) == '\\') {
                        bsCount++;
                        i++;
                    }
                    if (i < len && input.charAt(i) == '"') {
                        int literalBs = bsCount / 2;
                        for (int b = 0; b < literalBs; b++) {
                            current.append('\\');
                        }
                        if ((bsCount & 1) == 1) {
                            current.append('"');
                            i++;
                        } else {
                            if (inQuotes && i + 1 < len && input.charAt(i + 1) == '"') {
                                current.append('"');
                                i += 2;
                            } else {
                                inQuotes = !inQuotes;
                                i++;
                            }
                        }
                    } else {
                        for (int b = 0; b < bsCount; b++) {
                            current.append('\\');
                        }
                    }
                } else if (c == '"') {
                    if (inQuotes && i + 1 < len && input.charAt(i + 1) == '"') {
                        current.append('"');
                        i += 2;
                    } else {
                        inQuotes = !inQuotes;
                        i++;
                    }
                } else {
                    current.append(c);
                    i++;
                }
            }

            tokens.add(current.toString());
        }

        return List.copyOf(tokens);
    }

    private static String quotePosix(String arg) {
        if (arg.isEmpty()) {
            return "''";
        }
        if (POSIX_SAFE_BAREWORD.matcher(arg).matches()) {
            return arg;
        }
        StringBuilder sb = new StringBuilder(arg.length() + 4);
        sb.append('\'');
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '\'') {
                sb.append("'\\''");
            } else {
                sb.append(c);
            }
        }
        sb.append('\'');
        return sb.toString();
    }

    private static String quoteWindows(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        boolean needsQuotes = false;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000B' || c == '"') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) {
            return arg;
        }

        StringBuilder sb = new StringBuilder(arg.length() + 4);
        sb.append('"');
        int backslashes = 0;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '\\') {
                backslashes++;
            } else if (c == '"') {
                appendBackslashes(sb, backslashes * 2 + 1);
                sb.append('"');
                backslashes = 0;
            } else {
                appendBackslashes(sb, backslashes);
                sb.append(c);
                backslashes = 0;
            }
        }
        appendBackslashes(sb, backslashes * 2);
        sb.append('"');
        return sb.toString();
    }

    private static void appendBackslashes(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append('\\');
        }
    }
}
