package com.msp.cmdtokenizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandLineTokenizerTest {

    @Test
    @DisplayName("POSIX tokenization handles single quotes, double quotes, escapes, and adjacent concatenation")
    void posixTokenization() {
        CommandLineTokenizer posix = CommandLineTokenizer.forDialect(ShellDialect.POSIX);

        assertEquals(
                List.of("git", "commit", "-m", "fix: \"quoted\" & $var"),
                posix.tokenize("git commit -m \"fix: \\\"quoted\\\" & \\$var\"")
        );

        // Single quotes preserve everything literally (including backslashes)
        assertEquals(
                List.of("echo", "a\\b\\c $HOME"),
                posix.tokenize("echo 'a\\b\\c $HOME'")
        );

        // Adjacent segment concatenation: foo"bar"'baz' -> foobarbaz
        assertEquals(
                List.of("foobarbaz", "", ""),
                posix.tokenize("foo\"bar\"'baz' \"\" ''")
        );

        // Unquoted backslash escape and line continuation
        assertEquals(
                List.of("hello world", "joined"),
                posix.tokenize("hello\\ world joi\\\nned")
        );

        // Inside double quotes, backslash before non-special char is preserved
        assertEquals(
                List.of("a\\nb"),
                posix.tokenize("\"a\\nb\"")
        );
    }

    @Test
    @DisplayName("POSIX quoting prevents shell injection and round-trips cleanly")
    void posixQuotingRoundTrip() {
        CommandLineTokenizer posix = CommandLineTokenizer.forDialect(ShellDialect.POSIX);

        List<String> adversarial = List.of(
                "echo",
                "",
                "simple-arg_1.0",
                "hello world",
                "it's a 'test'",
                "$(rm -rf /); `id` | cat /etc/passwd",
                "back\\slash\\end\\",
                "line1\nline2"
        );

        String formatted = posix.format(adversarial);
        assertEquals(adversarial, posix.tokenize(formatted));
        assertTrue(formatted.contains("'$(rm -rf /); `id` | cat /etc/passwd'"));
        assertTrue(formatted.contains("'it'\\''s a '\\''test'\\'''"));
    }

    @Test
    @DisplayName("Windows MSVC CommandLineToArgvW rules handle backslashes before quotes accurately")
    void windowsMsvcTokenization() {
        CommandLineTokenizer win = CommandLineTokenizer.forDialect(ShellDialect.WINDOWS);

        // Official MSDN CommandLineToArgvW examples
        assertEquals(List.of("abc", "d", "e"), win.tokenize("\"abc\" d e"));
        assertEquals(List.of("a\\\\\\b", "de fg", "h"), win.tokenize("a\\\\\\b d\"e f\"g h"));
        assertEquals(List.of("a\\\"b", "c", "d"), win.tokenize("a\\\\\\\"b c d"));
        assertEquals(List.of("a\\\\b c", "d", "e"), win.tokenize("a\\\\\\\\\"b c\" d e"));

        // Empty quoted argument and doubled quote inside quotes
        assertEquals(List.of("", "a\"b"), win.tokenize("\"\" \"a\"\"b\""));

        // Trailing backslash inside quoted directory path
        assertEquals(List.of("C:\\Program Files\\"), win.tokenize("\"C:\\Program Files\\\\\""));
    }

    @Test
    @DisplayName("Windows quoting escapes backslashes before quotes and round-trips cleanly")
    void windowsQuotingRoundTrip() {
        CommandLineTokenizer win = CommandLineTokenizer.forDialect(ShellDialect.WINDOWS);

        List<String> args = List.of(
                "cmd.exe",
                "",
                "plain_arg",
                "C:\\Program Files\\",
                "C:\\Program Files\\My App\\",
                "say \"hello \\\"world\\\"\"\\",
                "a\\\\\"b",
                "tab\there"
        );

        String formatted = win.format(args);
        assertEquals(args, win.tokenize(formatted));
        assertTrue(formatted.contains("\"C:\\Program Files\\\\\""));
    }

    @Test
    @DisplayName("CommandLineBuilder builds fluent argument lists and escaped command strings")
    void commandLineBuilder() {
        CommandLineBuilder builder = CommandLineBuilder.forDialect(ShellDialect.POSIX)
                .executable("curl")
                .flag("-H", "Authorization: Bearer tok'en")
                .arg("https://example.com/api?a=1&b=2");

        assertEquals(
                List.of("curl", "-H", "Authorization: Bearer tok'en", "https://example.com/api?a=1&b=2"),
                builder.arguments()
        );
        String cmd = builder.build();
        assertEquals(builder.arguments(), CommandLineTokenizer.tokenize(cmd, ShellDialect.POSIX));
    }

    @Test
    @DisplayName("Error conditions: unterminated quotes, trailing backslash, and NUL bytes")
    void errorConditions() {
        CommandLineTokenizer posix = CommandLineTokenizer.forDialect(ShellDialect.POSIX);

        assertThrows(CommandLineParseException.class, () -> posix.tokenize("echo 'unterminated"));
        assertThrows(CommandLineParseException.class, () -> posix.tokenize("echo \"unterminated"));
        assertThrows(CommandLineParseException.class, () -> posix.tokenize("echo trailing\\"));
        assertThrows(CommandLineParseException.class, () -> posix.tokenize("echo \0bad"));
        assertThrows(IllegalArgumentException.class, () -> posix.quote("bad\0arg"));
        assertThrows(IllegalArgumentException.class, () -> CommandLineTokenizer.quote("bad\0arg", ShellDialect.WINDOWS));

        assertEquals(List.of(), posix.tokenize("   \t\n  "));
        assertEquals(List.of(), CommandLineTokenizer.tokenize("   \t  ", ShellDialect.WINDOWS));
    }
}
