package com.msp.dlq;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for DeadLetterMessage entries using ConcurrentHashMap.
 *
 * @param <T> payload type
 */
public class InMemoryDeadLetterStore<T> implements DeadLetterStore<T> {

    private final Map<String, DeadLetterMessage<T>> storage = new ConcurrentHashMap<>();

    @Override
    public void save(DeadLetterMessage<T> message) {
        storage.put(message.id(), message);
    }

    @Override
    public void update(DeadLetterMessage<T> message) {
        storage.put(message.id(), message);
    }

    @Override
    public Optional<DeadLetterMessage<T>> get(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<DeadLetterMessage<T>> getAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public boolean delete(String id) {
        return storage.remove(id) != null;
    }

    @Override
    public int clear() {
        int count = storage.size();
        storage.clear();
        return count;
    }
}
