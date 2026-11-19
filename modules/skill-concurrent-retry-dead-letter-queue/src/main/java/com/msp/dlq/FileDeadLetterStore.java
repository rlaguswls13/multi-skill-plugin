package com.msp.dlq;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * File-backed persistent dead letter store with thread-safe atomic append and in-memory caching.
 *
 * @param <T> payload type
 */
public class FileDeadLetterStore<T> implements DeadLetterStore<T> {

    private final Path storageFile;
    private final Function<T, String> serializer;
    private final Function<String, T> deserializer;
    private final Map<String, DeadLetterMessage<T>> cache = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public FileDeadLetterStore(Path storageFile, Function<T, String> serializer, Function<String, T> deserializer) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer cannot be null");
        loadExistingFile();
    }

    @SuppressWarnings("unchecked")
    public static FileDeadLetterStore<String> ofString(Path storageFile) {
        return new FileDeadLetterStore<>(storageFile, Function.identity(), Function.identity());
    }

    private void loadExistingFile() {
        if (!Files.exists(storageFile)) {
            return;
        }
        rwLock.writeLock().lock();
        try (BufferedReader reader = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                DeadLetterMessage<T> msg = parseLine(line);
                if (msg != null) {
                    cache.put(msg.id(), msg);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read dead-letter file: " + storageFile, e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void save(DeadLetterMessage<T> message) {
        rwLock.writeLock().lock();
        try {
            cache.put(message.id(), message);
            ensureParentDirs();
            try (BufferedWriter writer = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(formatLine(message));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to append dead-letter message to file", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void update(DeadLetterMessage<T> message) {
        rwLock.writeLock().lock();
        try {
            cache.put(message.id(), message);
            rewriteFile();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public Optional<DeadLetterMessage<T>> get(String id) {
        rwLock.readLock().lock();
        try {
            return Optional.ofNullable(cache.get(id));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public List<DeadLetterMessage<T>> getAll() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(cache.values());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public boolean delete(String id) {
        rwLock.writeLock().lock();
        try {
            boolean removed = (cache.remove(id) != null);
            if (removed) {
                rewriteFile();
            }
            return removed;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public int clear() {
        rwLock.writeLock().lock();
        try {
            int count = cache.size();
            cache.clear();
            rewriteFile();
            return count;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void ensureParentDirs() throws IOException {
        Path parent = storageFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private void rewriteFile() {
        try {
            ensureParentDirs();
            try (BufferedWriter writer = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (DeadLetterMessage<T> msg : cache.values()) {
                    writer.write(formatLine(msg));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite dead-letter file", e);
        }
    }

    private String formatLine(DeadLetterMessage<T> msg) {
        // ID\tSTATUS\tATTEMPTS\tTIMESTAMP\tERROR_TYPE\tESCAPED_MSG\tESCAPED_PAYLOAD
        String rawPayload = serializer.apply(msg.payload());
        String encodedPayload = Base64.getEncoder().encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
        String encodedMsg = Base64.getEncoder().encodeToString(msg.errorMessage().getBytes(StandardCharsets.UTF_8));
        return String.join("\t",
                msg.id(),
                msg.status().name(),
                String.valueOf(msg.attempts()),
                msg.quarantinedAt().toString(),
                msg.errorType(),
                encodedMsg,
                encodedPayload
        );
    }

    private DeadLetterMessage<T> parseLine(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 7) {
            return null;
        }
        String id = parts[0];
        MessageStatus status = MessageStatus.valueOf(parts[1]);
        int attempts = Integer.parseInt(parts[2]);
        Instant timestamp = Instant.parse(parts[3]);
        String errorType = parts[4];
        String errorMessage = new String(Base64.getDecoder().decode(parts[5]), StandardCharsets.UTF_8);
        String rawPayload = new String(Base64.getDecoder().decode(parts[6]), StandardCharsets.UTF_8);
        T payload = deserializer.apply(rawPayload);
        return new DeadLetterMessage<>(id, payload, errorType, errorMessage, "", attempts, timestamp, Map.of(), status);
    }
}
