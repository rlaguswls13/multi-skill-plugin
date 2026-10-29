package com.msp.shutdown;

import java.time.Duration;
import java.util.*;

/**
 * Comprehensive summary report generated after shutdown execution.
 */
public class ShutdownReport {

    private final boolean timedOut;
    private final Duration totalDuration;
    private final List<ComponentShutdownResult> componentResults;
    private final Map<String, ComponentShutdownResult> resultMap;

    public ShutdownReport(boolean timedOut, Duration totalDuration, List<ComponentShutdownResult> componentResults) {
        this.timedOut = timedOut;
        this.totalDuration = Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        this.componentResults = List.copyOf(Objects.requireNonNull(componentResults, "componentResults must not be null"));

        Map<String, ComponentShutdownResult> map = new LinkedHashMap<>();
        for (ComponentShutdownResult r : this.componentResults) {
            map.put(r.name(), r);
        }
        this.resultMap = Collections.unmodifiableMap(map);
    }

    /**
     * True if all components were cleaned up successfully without exceptions or timeouts.
     */
    public boolean isSuccessful() {
        return !timedOut && componentResults.stream().allMatch(ComponentShutdownResult::isSuccess);
    }

    /**
     * True if shutdown was cut off due to timeout deadline expiration.
     */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * Total elapsed wall-clock time for the shutdown process.
     */
    public Duration totalDuration() {
        return totalDuration;
    }

    /**
     * All component shutdown results in execution order.
     */
    public List<ComponentShutdownResult> componentResults() {
        return componentResults;
    }

    /**
     * Component results mapped by name.
     */
    public Map<String, ComponentShutdownResult> resultMap() {
        return resultMap;
    }

    /**
     * Collects all exceptions thrown across failed components.
     */
    public List<Throwable> exceptions() {
        List<Throwable> list = new ArrayList<>();
        for (ComponentShutdownResult res : componentResults) {
            if (res.error() != null) {
                list.add(res.error());
            }
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public String toString() {
        return "ShutdownReport{successful=" + isSuccessful() +
                ", timedOut=" + timedOut +
                ", duration=" + totalDuration.toMillis() + "ms" +
                ", components=" + componentResults.size() + "}";
    }
}
