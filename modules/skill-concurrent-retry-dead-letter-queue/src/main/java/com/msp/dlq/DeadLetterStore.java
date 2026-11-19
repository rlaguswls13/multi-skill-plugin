package com.msp.dlq;

import java.util.List;
import java.util.Optional;

/**
 * Storage SPI for persisting and retrieving dead letter messages.
 *
 * @param <T> payload type
 */
public interface DeadLetterStore<T> {

    void save(DeadLetterMessage<T> message);

    void update(DeadLetterMessage<T> message);

    Optional<DeadLetterMessage<T>> get(String id);

    List<DeadLetterMessage<T>> getAll();

    boolean delete(String id);

    int clear();
}
