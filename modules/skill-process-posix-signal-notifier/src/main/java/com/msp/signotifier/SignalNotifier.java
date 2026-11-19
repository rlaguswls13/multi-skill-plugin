package com.msp.signotifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JVM Graceful Shutdown & POSIX OS Signal Asynchronous Notification Registry.
 * Coordinates prioritized dispatch of signal listeners with per-listener timeout guards.
 */
public class SignalNotifier implements AutoCloseable {

    private static final SignalNotifier INSTANCE = new SignalNotifier();

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    public static final int DEFAULT_PRIORITY = 5;

    private final Map<SignalType, List<RegisteredListener>> listenerMap = new ConcurrentHashMap<>();
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sig-notifier-worker");
        t.setDaemon(true);
        return t;
    });

    private Thread shutdownHookThread;
    private volatile boolean shutdownHookEnabled = false;

    public SignalNotifier() {
        for (SignalType type : SignalType.values()) {
            listenerMap.put(type, new CopyOnWriteArrayList<>());
        }
    }

    public static SignalNotifier getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a signal listener with default priority (5) and default timeout (5s).
     */
    public ListenerRegistration registerListener(SignalType type, SignalListener listener) {
        return registerListener("Listener-" + UUID.randomUUID().toString().substring(0, 8), type, listener, DEFAULT_PRIORITY, DEFAULT_TIMEOUT);
    }

    /**
     * Registers a signal listener with explicit priority and timeout.
     */
    public ListenerRegistration registerListener(SignalType type, SignalListener listener, int priority, Duration timeout) {
        return registerListener("Listener-" + UUID.randomUUID().toString().substring(0, 8), type, listener, priority, timeout);
    }

    /**
     * Registers a named signal listener with explicit priority and timeout.
     */
    public ListenerRegistration registerListener(String name, SignalType type, SignalListener listener, int priority, Duration timeout) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        String id = UUID.randomUUID().toString();
        long seq = sequenceGenerator.incrementAndGet();
        RegisteredListener reg = new RegisteredListener(id, name, type, priority, seq, timeout, listener);

        listenerMap.get(type).add(reg);

        return new ListenerRegistration(id, name, type, priority, timeout, () -> unregister(type, id));
    }

    public boolean unregister(SignalType type, String listenerId) {
        List<RegisteredListener> list = listenerMap.get(type);
        if (list != null) {
            return list.removeIf(l -> l.id().equals(listenerId));
        }
        return false;
    }

    public boolean unregister(String listenerId) {
        boolean removed = false;
        for (List<RegisteredListener> list : listenerMap.values()) {
            if (list.removeIf(l -> l.id().equals(listenerId))) {
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Dispatches a signal to all registered listeners matching the given signal type in priority order.
     */
    public SignalDispatchReport notifySignal(SignalType type) {
        return notifySignal(SignalEvent.of(type));
    }

    /**
     * Dispatches a signal event to all registered listeners.
     */
    public SignalDispatchReport notifySignal(SignalEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Instant start = Instant.now();

        List<RegisteredListener> registered = listenerMap.get(event.type());
        if (registered == null || registered.isEmpty()) {
            return new SignalDispatchReport(event, start, Instant.now(), Duration.ZERO, List.of());
        }

        // Sort by priority descending (higher first), then sequence ascending (FIFO)
        List<RegisteredListener> sorted = new ArrayList<>(registered);
        sorted.sort(Comparator
                .comparingInt(RegisteredListener::priority).reversed()
                .thenComparingLong(RegisteredListener::sequence));

        List<ListenerExecutionResult> results = new ArrayList<>();

        for (RegisteredListener rl : sorted) {
            Instant itemStart = Instant.now();
            SignalExecutionStatus status;
            Throwable error = null;

            java.util.concurrent.atomic.AtomicReference<Throwable> originalError = new java.util.concurrent.atomic.AtomicReference<>();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    rl.listener().onSignal(event);
                } catch (Throwable t) {
                    originalError.set(t);
                    throw (t instanceof RuntimeException re) ? re : new RuntimeException(t);
                }
            }, executor);

            try {
                future.get(rl.timeout().toMillis(), TimeUnit.MILLISECONDS);
                status = SignalExecutionStatus.SUCCESS;
            } catch (TimeoutException e) {
                future.cancel(true);
                status = SignalExecutionStatus.TIMEOUT;
                error = e;
            } catch (Throwable t) {
                status = SignalExecutionStatus.FAILED;
                error = originalError.get() != null ? originalError.get() : (t.getCause() != null ? t.getCause() : t);
            }

            Duration itemDuration = Duration.between(itemStart, Instant.now());
            results.add(new ListenerExecutionResult(rl.id(), rl.name(), event.type(), status, itemDuration, error));
        }

        Instant end = Instant.now();
        return new SignalDispatchReport(event, start, end, Duration.between(start, end), results);
    }

    /**
     * Enables automatic JVM shutdown hook bridging.
     */
    public synchronized void enableShutdownHook() {
        if (!shutdownHookEnabled) {
            shutdownHookThread = new Thread(() -> {
                notifySignal(SignalEvent.of(SignalType.SIGTERM, true));
            }, "posix-signal-shutdown-hook");
            Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            shutdownHookEnabled = true;
        }
    }

    /**
     * Disables the JVM shutdown hook.
     */
    public synchronized void disableShutdownHook() {
        if (shutdownHookEnabled && shutdownHookThread != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
            } catch (IllegalStateException ignored) {
                // JVM may already be shutting down
            }
            shutdownHookThread = null;
            shutdownHookEnabled = false;
        }
    }

    public boolean isShutdownHookEnabled() {
        return shutdownHookEnabled;
    }

    public void clear() {
        for (List<RegisteredListener> list : listenerMap.values()) {
            list.clear();
        }
    }

    @Override
    public void close() {
        disableShutdownHook();
        executor.shutdownNow();
    }

    private record RegisteredListener(
            String id,
            String name,
            SignalType type,
            int priority,
            long sequence,
            Duration timeout,
            SignalListener listener
    ) {}
}
