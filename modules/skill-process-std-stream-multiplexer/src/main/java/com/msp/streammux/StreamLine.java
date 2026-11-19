package com.msp.streammux;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Representation of a single multiplexed line of output from a process stream.
 */
public record StreamLine(
        long sequence,
        String processTag,
        StreamType streamType,
        Instant timestamp,
        String text
) {

    public StreamLine {
        Objects.requireNonNull(processTag, "processTag cannot be null");
        Objects.requireNonNull(streamType, "streamType cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
    }

    /**
     * Standard formatted representation:
     * 2026-11-01T13:20:00.123Z [processTag] [STDOUT] line text
     */
    public String formatted() {
        return DateTimeFormatter.ISO_INSTANT.format(timestamp)
                + " [" + processTag + "] [" + streamType.name() + "] " + text;
    }

    /**
     * Formatted with elapsed milliseconds relative to base time:
     * +124ms [processTag] [STDERR] line text
     */
    public String formattedElapsed(Instant baseTime) {
        long elapsedMillis = timestamp.toEpochMilli() - baseTime.toEpochMilli();
        return "+" + elapsedMillis + "ms [" + processTag + "] [" + streamType.name() + "] " + text;
    }

    @Override
    public String toString() {
        return formatted();
    }
}
