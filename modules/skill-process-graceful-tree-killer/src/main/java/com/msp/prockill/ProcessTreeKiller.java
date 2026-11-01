package com.msp.prockill;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gracefully and reliably terminates an entire OS process tree using Java 9+ {@link ProcessHandle}.
 * <p>
 * Implements a robust two-stage shutdown protocol:
 * <ol>
 *   <li>Stage 1: Sends graceful {@link ProcessHandle#destroy()} signals in bottom-up order
 *       (deepest child processes first, finishing at the root parent process).</li>
 *   <li>Stage 2: Awaits graceful exit up to a configurable grace period timeout.</li>
 *   <li>Stage 3: Issues {@link ProcessHandle#destroyForcibly()} for any surviving processes.</li>
 * </ol>
 */
public final class ProcessTreeKiller {

    private final ProcessKillConfig config;

    public ProcessTreeKiller() {
        this(ProcessKillConfig.defaultConfig());
    }

    public ProcessTreeKiller(ProcessKillConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public static ProcessTreeKiller create() {
        return new ProcessTreeKiller();
    }

    public static ProcessKillReport kill(long pid) {
        return new ProcessTreeKiller().execute(pid);
    }

    public static ProcessKillReport kill(Process process) {
        Objects.requireNonNull(process, "process cannot be null");
        return kill(process.toHandle());
    }

    public static ProcessKillReport kill(ProcessHandle handle) {
        Objects.requireNonNull(handle, "handle cannot be null");
        return kill(handle.pid());
    }

    public static ProcessKillReport kill(long pid, Duration gracePeriod) {
        return new ProcessTreeKiller(ProcessKillConfig.builder().gracePeriod(gracePeriod).build()).execute(pid);
    }

    public static List<ProcessInfo> inspectTree(long pid) {
        return new ProcessTreeKiller(ProcessKillConfig.builder().dryRun(true).build()).execute(pid).terminatedGracefully();
    }

    public ProcessTreeKiller gracePeriod(Duration gracePeriod) {
        return new ProcessTreeKiller(ProcessKillConfig.builder()
                .gracePeriod(gracePeriod)
                .forceKillTimeout(config.forceKillTimeout())
                .safeMode(config.safeMode())
                .dryRun(config.dryRun())
                .allowKillCurrentProcess(config.allowKillCurrentProcess())
                .build());
    }

    public ProcessTreeKiller forceKillTimeout(Duration forceKillTimeout) {
        return new ProcessTreeKiller(ProcessKillConfig.builder()
                .gracePeriod(config.gracePeriod())
                .forceKillTimeout(forceKillTimeout)
                .safeMode(config.safeMode())
                .dryRun(config.dryRun())
                .allowKillCurrentProcess(config.allowKillCurrentProcess())
                .build());
    }

    public ProcessTreeKiller safeMode(boolean safeMode) {
        return new ProcessTreeKiller(ProcessKillConfig.builder()
                .gracePeriod(config.gracePeriod())
                .forceKillTimeout(config.forceKillTimeout())
                .safeMode(safeMode)
                .dryRun(config.dryRun())
                .allowKillCurrentProcess(config.allowKillCurrentProcess())
                .build());
    }

    public ProcessTreeKiller dryRun(boolean dryRun) {
        return new ProcessTreeKiller(ProcessKillConfig.builder()
                .gracePeriod(config.gracePeriod())
                .forceKillTimeout(config.forceKillTimeout())
                .safeMode(config.safeMode())
                .dryRun(dryRun)
                .allowKillCurrentProcess(config.allowKillCurrentProcess())
                .build());
    }

    public ProcessTreeKiller allowKillCurrentProcess(boolean allow) {
        return new ProcessTreeKiller(ProcessKillConfig.builder()
                .gracePeriod(config.gracePeriod())
                .forceKillTimeout(config.forceKillTimeout())
                .safeMode(config.safeMode())
                .dryRun(config.dryRun())
                .allowKillCurrentProcess(allow)
                .build());
    }

    public ProcessKillReport execute(Process process) {
        Objects.requireNonNull(process, "process cannot be null");
        return execute(process.toHandle().pid());
    }

    public ProcessKillReport execute(ProcessHandle handle) {
        Objects.requireNonNull(handle, "handle cannot be null");
        return execute(handle.pid());
    }

    /**
     * Executes the process tree termination protocol against the given root PID.
     *
     * @param pid the root PID to terminate
     * @return a {@link ProcessKillReport} summarizing all affected processes
     */
    public ProcessKillReport execute(long pid) {
        validateSafety(pid);

        long startTime = System.nanoTime();

        Optional<ProcessHandle> rootOpt = ProcessHandle.of(pid);
        if (rootOpt.isEmpty()) {
            return new ProcessKillReport(pid, List.of(), List.of(), List.of(), Duration.ZERO, config.dryRun());
        }

        ProcessHandle root = rootOpt.get();
        if (!root.isAlive()) {
            return new ProcessKillReport(pid, List.of(ProcessInfo.of(root)), List.of(), List.of(), Duration.ZERO, config.dryRun());
        }

        // Collect all descendants and root, sorted bottom-up (deepest first)
        List<ProcessHandle> bottomUpHandles = collectBottomUpHierarchy(root);

        // Dry run: inspect only
        if (config.dryRun()) {
            List<ProcessInfo> infos = bottomUpHandles.stream().map(ProcessInfo::of).toList();
            Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
            return new ProcessKillReport(pid, infos, List.of(), List.of(), duration, true);
        }

        // Stage 1: Graceful destroy (bottom-up)
        Map<Long, ProcessInfo> originalSnapshots = new LinkedHashMap<>();
        for (ProcessHandle handle : bottomUpHandles) {
            originalSnapshots.put(handle.pid(), ProcessInfo.of(handle));
            if (handle.isAlive()) {
                handle.destroy();
            }
        }

        // Stage 2: Await graceful termination
        awaitProcessesExit(bottomUpHandles, config.gracePeriod());

        List<ProcessInfo> terminatedGracefully = new ArrayList<>();
        List<ProcessHandle> survivors = new ArrayList<>();

        for (ProcessHandle handle : bottomUpHandles) {
            ProcessInfo snap = originalSnapshots.get(handle.pid());
            if (!handle.isAlive()) {
                terminatedGracefully.add(snap);
            } else {
                survivors.add(handle);
            }
        }

        // Stage 3: Forced kill for survivors
        List<ProcessInfo> killedForcibly = new ArrayList<>();
        List<ProcessInfo> failed = new ArrayList<>();

        if (!survivors.isEmpty()) {
            for (ProcessHandle handle : survivors) {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            }

            awaitProcessesExit(survivors, config.forceKillTimeout());

            for (ProcessHandle handle : survivors) {
                ProcessInfo snap = originalSnapshots.get(handle.pid());
                if (!handle.isAlive()) {
                    killedForcibly.add(snap);
                } else {
                    failed.add(snap);
                }
            }
        }

        Duration totalDuration = Duration.ofNanos(System.nanoTime() - startTime);
        return new ProcessKillReport(pid, terminatedGracefully, killedForcibly, failed, totalDuration, false);
    }

    private void validateSafety(long pid) {
        if (!config.safeMode() || config.dryRun()) {
            return;
        }

        if (pid <= 0) {
            throw new IllegalArgumentException("SafeMode blocked termination of system PID: " + pid);
        }

        if (pid == 1) {
            throw new IllegalArgumentException("SafeMode blocked termination of init/systemd PID 1");
        }

        long selfPid = ProcessHandle.current().pid();
        if (pid == selfPid && !config.allowKillCurrentProcess()) {
            throw new IllegalArgumentException("SafeMode blocked termination of current JVM PID: " + pid);
        }
    }

    private List<ProcessHandle> collectBottomUpHierarchy(ProcessHandle root) {
        // Collect descendants
        List<ProcessHandle> all = new ArrayList<>();
        root.descendants().filter(ProcessHandle::isAlive).forEach(all::add);
        all.add(root);

        // Sort descending by depth from root (deepest first)
        all.sort((p1, p2) -> Integer.compare(computeDepth(p2, root), computeDepth(p1, root)));
        return all;
    }

    private int computeDepth(ProcessHandle handle, ProcessHandle root) {
        int depth = 0;
        ProcessHandle cur = handle;
        while (cur != null && !cur.equals(root)) {
            depth++;
            cur = cur.parent().orElse(null);
        }
        return depth;
    }

    private void awaitProcessesExit(List<ProcessHandle> handles, Duration timeout) {
        List<CompletableFuture<ProcessHandle>> exitFutures = handles.stream()
                .map(ProcessHandle::onExit)
                .toList();

        CompletableFuture<Void> all = CompletableFuture.allOf(exitFutures.toArray(new CompletableFuture[0]));
        try {
            long ms = Math.max(1, timeout.toMillis());
            all.get(ms, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Wait period elapsed; check which processes are still alive
        }
    }
}
