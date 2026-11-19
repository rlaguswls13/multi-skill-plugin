package com.msp.dlq;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Thread-safe implementation of {@link DeadLetterQueue} orchestrating poison message quarantine,
 * retry replays, metrics tracking, and persistence.
 *
 * @param <T> message payload type
 */
public class ConcurrentDeadLetterQueue<T> implements DeadLetterQueue<T> {

    private final DeadLetterStore<T> store;
    private final AtomicLong totalQuarantined = new AtomicLong(0);
    private final AtomicLong replaySuccessCount = new AtomicLong(0);
    private final AtomicLong replayFailureCount = new AtomicLong(0);
    private final AtomicLong discardedCount = new AtomicLong(0);

    public ConcurrentDeadLetterQueue(DeadLetterStore<T> store) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        // Initialize totalQuarantined count from existing store items
        for (DeadLetterMessage<T> msg : store.getAll()) {
            totalQuarantined.incrementAndGet();
            if (msg.status() == MessageStatus.REPLAYED) {
                replaySuccessCount.incrementAndGet();
            } else if (msg.status() == MessageStatus.DISCARDED) {
                discardedCount.incrementAndGet();
            }
        }
    }

    public static <T> ConcurrentDeadLetterQueue<T> inMemory() {
        return new ConcurrentDeadLetterQueue<>(new InMemoryDeadLetterStore<>());
    }

    public static <T> ConcurrentDeadLetterQueue<T> of(DeadLetterStore<T> store) {
        return new ConcurrentDeadLetterQueue<>(store);
    }

    public static ConcurrentDeadLetterQueue<String> filePersistent(Path path) {
        return new ConcurrentDeadLetterQueue<>(FileDeadLetterStore.ofString(path));
    }

    @Override
    public void enqueue(T payload, Throwable cause, int attempts, Map<String, String> metadata) {
        DeadLetterMessage<T> message = DeadLetterMessage.of(payload, cause, attempts, metadata);
        enqueue(message);
    }

    @Override
    public void enqueue(DeadLetterMessage<T> message) {
        Objects.requireNonNull(message, "message cannot be null");
        store.save(message);
        totalQuarantined.incrementAndGet();
    }

    @Override
    public Optional<DeadLetterMessage<T>> findById(String id) {
        return store.get(id);
    }

    @Override
    public List<DeadLetterMessage<T>> listAll() {
        return store.getAll();
    }

    @Override
    public List<DeadLetterMessage<T>> listQuarantined() {
        return search(m -> m.status() == MessageStatus.QUARANTINED);
    }

    @Override
    public List<DeadLetterMessage<T>> search(Predicate<DeadLetterMessage<T>> filter) {
        Objects.requireNonNull(filter, "filter cannot be null");
        return store.getAll().stream().filter(filter).toList();
    }

    @Override
    public List<DeadLetterMessage<T>> findByErrorType(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return List.of();
        }
        return search(m -> m.errorType().contains(errorType));
    }

    @Override
    public List<DeadLetterMessage<T>> findByTimeRange(Instant from, Instant to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        return search(m -> !m.quarantinedAt().isBefore(from) && !m.quarantinedAt().isAfter(to));
    }

    @Override
    public List<DeadLetterMessage<T>> findByPayloadKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return search(m -> m.payload() != null && m.payload().toString().contains(keyword));
    }

    @Override
    public long replay(Consumer<DeadLetterMessage<T>> retryHandler) {
        Objects.requireNonNull(retryHandler, "retryHandler cannot be null");
        List<DeadLetterMessage<T>> quarantined = listQuarantined();
        long successCount = 0;
        for (DeadLetterMessage<T> msg : quarantined) {
            if (executeReplay(msg, retryHandler)) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public boolean replayOne(String id, Consumer<DeadLetterMessage<T>> retryHandler) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(retryHandler, "retryHandler cannot be null");
        Optional<DeadLetterMessage<T>> opt = store.get(id);
        if (opt.isEmpty() || opt.get().status() != MessageStatus.QUARANTINED) {
            return false;
        }
        return executeReplay(opt.get(), retryHandler);
    }

    @Override
    public long replayBatch(List<String> ids, Consumer<DeadLetterMessage<T>> retryHandler) {
        Objects.requireNonNull(ids, "ids cannot be null");
        Objects.requireNonNull(retryHandler, "retryHandler cannot be null");
        long success = 0;
        for (String id : ids) {
            if (replayOne(id, retryHandler)) {
                success++;
            }
        }
        return success;
    }

    private boolean executeReplay(DeadLetterMessage<T> msg, Consumer<DeadLetterMessage<T>> retryHandler) {
        try {
            retryHandler.accept(msg);
            store.update(msg.withStatus(MessageStatus.REPLAYED));
            replaySuccessCount.incrementAndGet();
            return true;
        } catch (Throwable ex) {
            replayFailureCount.incrementAndGet();
            store.update(msg.withAttempts(msg.attempts() + 1));
            return false;
        }
    }

    @Override
    public boolean discard(String id) {
        Optional<DeadLetterMessage<T>> opt = store.get(id);
        if (opt.isPresent() && opt.get().status() == MessageStatus.QUARANTINED) {
            store.update(opt.get().withStatus(MessageStatus.DISCARDED));
            discardedCount.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public int purge() {
        int cleared = store.clear();
        discardedCount.addAndGet(cleared);
        return cleared;
    }

    @Override
    public int size() {
        return listQuarantined().size();
    }

    @Override
    public DlqMetrics metrics() {
        long active = listQuarantined().size();
        return new DlqMetrics(
                totalQuarantined.get(),
                active,
                replaySuccessCount.get(),
                replayFailureCount.get(),
                discardedCount.get()
        );
    }
}
