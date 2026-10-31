package com.msp.processpipe;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable definition of a single execution stage in a process pipeline.
 *
 * @param command     the command line and its arguments (non-empty)
 * @param environment optional stage-specific environment variables (or empty map)
 * @param workingDir  optional stage-specific working directory (or null for default)
 */
public record PipelineStage(List<String> command, Map<String, String> environment, File workingDir) {

    public PipelineStage {
        Objects.requireNonNull(command, "command must not be null");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        command = List.copyOf(command);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public PipelineStage(List<String> command) {
        this(command, Map.of(), null);
    }

    public PipelineStage(String... command) {
        this(command == null ? List.of() : List.of(command), Map.of(), null);
    }

    public static PipelineStage of(String... command) {
        return new PipelineStage(command);
    }

    public static PipelineStage of(List<String> command) {
        return new PipelineStage(command);
    }

    public static PipelineStage of(List<String> command, Map<String, String> environment, File workingDir) {
        return new PipelineStage(command, environment, workingDir);
    }
}
