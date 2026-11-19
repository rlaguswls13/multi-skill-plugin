package com.msp.cmdtokenizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for assembling injection-safe command lines for a target {@link ShellDialect}.
 */
public final class CommandLineBuilder {

    private final ShellDialect dialect;
    private final List<String> args = new ArrayList<>();

    private CommandLineBuilder(ShellDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
    }

    public static CommandLineBuilder forDialect(ShellDialect dialect) {
        return new CommandLineBuilder(dialect);
    }

    public static CommandLineBuilder forCurrentOs() {
        return new CommandLineBuilder(ShellDialect.currentOs());
    }

    public CommandLineBuilder executable(String executable) {
        Objects.requireNonNull(executable, "executable must not be null");
        if (executable.isBlank()) {
            throw new IllegalArgumentException("executable must not be blank");
        }
        args.add(executable);
        return this;
    }

    public CommandLineBuilder arg(String argument) {
        args.add(Objects.requireNonNull(argument, "argument must not be null"));
        return this;
    }

    public CommandLineBuilder args(String... arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        for (String a : arguments) {
            arg(a);
        }
        return this;
    }

    public CommandLineBuilder args(Collection<String> arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        for (String a : arguments) {
            arg(a);
        }
        return this;
    }

    public CommandLineBuilder flag(String flagName, String value) {
        arg(flagName);
        arg(value);
        return this;
    }

    /**
     * Returns an immutable snapshot of the raw argument list (ready for {@link ProcessBuilder}).
     */
    public List<String> arguments() {
        return List.copyOf(args);
    }

    /**
     * Formats the accumulated arguments into a safely escaped command-line string.
     */
    public String build() {
        return CommandLineTokenizer.format(args, dialect);
    }
}
