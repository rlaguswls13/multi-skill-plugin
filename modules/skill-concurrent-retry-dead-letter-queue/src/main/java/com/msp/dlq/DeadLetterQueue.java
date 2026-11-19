package com.msp.dlq;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Dead-Letter Queue (DLQ) Manager & Poison Message Quarantine Orchestrator.
 *
 * @param <T> message payload type
 */
public interface DeadLetterQueue<T> {

    void enqueue(T payload, Throwable cause, int attempts, Map<String, String> metadata);

    default void enqueue(T payload, Throwable cause, int attempts) {
        enqueue(payload, cause, attempts, Map.of());
    }

    void enqueue(DeadLetterMessage<T> message);

    Optional<DeadLetterMessage<T>> findById(String id);

    List<DeadLetterMessage<T>> listAll();

    List<DeadLetterMessage<T>> listQuarantined();

    List<DeadLetterMessage<T>> search(Predicate<DeadLetterMessage<T>> filter);

    List<DeadLetterMessage<T>> findByErrorType(String errorType);

    List<DeadLetterMessage<T>> findByTimeRange(Instant from, Instant to);

    List<DeadLetterMessage<T>> findByPayloadKeyword(String keyword);

    /**
     * Reprocesses all active quarantined messages using the given retry handler.
     * Successfully reprocessed messages are marked as {@link MessageStatus#REPLAYED}.
     *
     * @param retryHandler callback processing each dead letter message
     * @return number of successfully replayed messages
     */
    long replay(Consumer<DeadLetterMessage<T>> retryHandler);

    /**
     * Reprocesses a single quarantined message by ID.
     */
    boolean replayOne(String id, Consumer<DeadLetterMessage<T>> retryHandler);

    /**
     * Reprocesses a specific batch of message IDs.
     */
    long replayBatch(List<String> ids, Consumer<DeadLetterMessage<T>> retryHandler);

    /**
     * Marks a quarantined message as {@link MessageStatus#DISCARDED}.
     */
    boolean discard(String id);

    /**
     * Clears all messages from the dead letter store.
     *
     * @return count of purged messages
     */
    int purge();

    int size();

    DlqMetrics metrics();
}
