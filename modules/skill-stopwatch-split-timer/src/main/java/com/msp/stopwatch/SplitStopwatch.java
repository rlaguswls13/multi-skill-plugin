package com.msp.stopwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Multi-segment split timer and profiler with ASCII timeline visualization.
 */
public final class SplitStopwatch {

    private final String taskName;
    private long startNanos;
    private long lastLapNanos;
    private long stopNanos;
    private boolean running;
    private final List<SplitLap> laps = new ArrayList<>();

    public SplitStopwatch(String taskName) {
        this.taskName = Objects.requireNonNull(taskName, "taskName must not be null");
    }

    public static SplitStopwatch start(String taskName) {
        SplitStopwatch sw = new SplitStopwatch(taskName);
        sw.start();
        return sw;
    }

    public void start() {
        if (running) {
            throw new IllegalStateException("Stopwatch is already running");
        }
        this.startNanos = System.nanoTime();
        this.lastLapNanos = this.startNanos;
        this.stopNanos = 0;
        this.running = true;
        this.laps.clear();
    }

    /**
     * Records a split point with the given lap name.
     *
     * @param lapName name of the finished step/segment
     * @return the recorded SplitLap
     */
    public SplitLap split(String lapName) {
        if (!running) {
            throw new IllegalStateException("Stopwatch is not running");
        }
        long now = System.nanoTime();
        long lapNanos = now - lastLapNanos;
        long totalSoFar = now - startNanos;
        this.lastLapNanos = now;

        SplitLap lap = new SplitLap(lapName, lapNanos, totalSoFar);
        laps.add(lap);
        return lap;
    }

    /**
     * Stops the stopwatch and records an optional final split if provided.
     */
    public void stop() {
        if (!running) {
            return;
        }
        this.stopNanos = System.nanoTime();
        this.running = false;
    }

    public long getTotalDurationNanos() {
        if (running) {
            return System.nanoTime() - startNanos;
        }
        return (stopNanos > 0 ? stopNanos : lastLapNanos) - startNanos;
    }

    public double getTotalDurationMillis() {
        return getTotalDurationNanos() / 1_000_000.0;
    }

    public List<SplitLap> getLaps() {
        return Collections.unmodifiableList(laps);
    }

    public String getTaskName() {
        return taskName;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Formats the split timeline as a clean ASCII report table.
     */
    public String prettyPrint() {
        long totalNanos = getTotalDurationNanos();
        double totalMs = totalNanos / 1_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Stopwatch '%s': total time = %.2f ms%n", taskName, totalMs));
        sb.append("------------------------------------------------------------\n");
        sb.append(String.format("%-25s %12s %12s %8s%n", "Split Step", "Lap (ms)", "Total (ms)", "%"));
        sb.append("------------------------------------------------------------\n");

        for (SplitLap lap : laps) {
            sb.append(String.format("%-25s %12.2f %12.2f %7.1f%%%n",
                    truncate(lap.name(), 25),
                    lap.lapMillis(),
                    lap.cumulativeMillis(),
                    lap.percentage(totalNanos)
            ));
        }
        sb.append("------------------------------------------------------------");
        return sb.toString();
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    @Override
    public String toString() {
        return prettyPrint();
    }
}
