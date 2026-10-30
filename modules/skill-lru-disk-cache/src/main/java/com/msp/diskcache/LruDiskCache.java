package com.msp.diskcache;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * File-system based LRU and TTL disk cache manager without external dependencies.
 * Each cache entry is stored as a self-contained binary file holding metadata and payload.
 * Thread-safe for concurrent read and write operations.
 */
public class LruDiskCache implements AutoCloseable {

    private static final int MAGIC = 0x4C525543; // 'LRUC'
    private static final short VERSION = 1;
    private static final int HEADER_FIXED_SIZE = 4 + 2 + 8 + 8 + 4; // magic(4), ver(2), exp(8), access(8), keyLen(4) = 26 bytes

    private final Path rootDir;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LruDiskCache(Path rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir must not be null");
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create cache root directory: " + rootDir, e);
        }
    }

    /**
     * Stores a binary payload in the disk cache with a given TTL.
     *
     * @param key  the unique cache key
     * @param data binary payload
     * @param ttl  time-to-live duration (null or zero duration for no expiration)
     * @throws IOException if disk write fails
     */
    public void put(String key, byte[] data, Duration ttl) throws IOException {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(data, "data must not be null");

        long now = System.currentTimeMillis();
        long expireAt = (ttl != null && !ttl.isZero() && !ttl.isNegative())
                ? now + ttl.toMillis()
                : 0L;

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(HEADER_FIXED_SIZE + keyBytes.length);
        header.putInt(MAGIC);
        header.putShort(VERSION);
        header.putLong(expireAt);
        header.putLong(now);
        header.putInt(keyBytes.length);
        header.put(keyBytes);
        header.flip();

        Path targetPath = pathForKey(key);
        Path tempPath = Files.createTempFile(rootDir, "lru-put-", ".tmp");

        lock.writeLock().lock();
        try {
            try (FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                channel.write(header);
                channel.write(ByteBuffer.wrap(data));
                channel.force(false);
            }

            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.setLastModifiedTime(targetPath, FileTime.fromMillis(now));
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            lock.writeLock().unlock();
        }
    }

    /**
     * Stores a string payload in the disk cache with a given TTL.
     */
    public void put(String key, String text, Duration ttl) throws IOException {
        Objects.requireNonNull(text, "text must not be null");
        put(key, text.getBytes(StandardCharsets.UTF_8), ttl);
    }

    /**
     * Retrieves binary payload for the key. If expired, automatically deletes the file and returns empty.
     * Updates the last access timestamp to maintain LRU order.
     *
     * @param key cache key
     * @return Optional containing data if present and not expired, empty otherwise
     * @throws IOException if disk read fails
     */
    public Optional<byte[]> get(String key) throws IOException {
        Objects.requireNonNull(key, "key must not be null");
        Path targetPath = pathForKey(key);

        lock.writeLock().lock(); // write lock needed as we touch lastAccessTime
        try {
            if (!Files.exists(targetPath)) {
                return Optional.empty();
            }

            long now = System.currentTimeMillis();
            try (RandomAccessFile raf = new RandomAccessFile(targetPath.toFile(), "rw");
                 FileChannel channel = raf.getChannel()) {

                long fileSize = channel.size();
                if (fileSize < HEADER_FIXED_SIZE) {
                    Files.deleteIfExists(targetPath);
                    return Optional.empty();
                }

                ByteBuffer fixedBuf = ByteBuffer.allocate(HEADER_FIXED_SIZE);
                channel.read(fixedBuf);
                fixedBuf.flip();

                int magic = fixedBuf.getInt();
                if (magic != MAGIC) {
                    Files.deleteIfExists(targetPath);
                    return Optional.empty();
                }

                short ver = fixedBuf.getShort();
                long expireAt = fixedBuf.getLong();
                long lastAccess = fixedBuf.getLong();
                int keyLen = fixedBuf.getInt();

                if (expireAt > 0 && now > expireAt) {
                    channel.close();
                    Files.deleteIfExists(targetPath);
                    return Optional.empty();
                }

                // Update last access time in header (offset 14)
                ByteBuffer accessBuf = ByteBuffer.allocate(8);
                accessBuf.putLong(now).flip();
                channel.position(14);
                channel.write(accessBuf);

                // Skip key bytes to reach payload
                long payloadOffset = HEADER_FIXED_SIZE + keyLen;
                if (fileSize < payloadOffset) {
                    Files.deleteIfExists(targetPath);
                    return Optional.empty();
                }

                channel.position(payloadOffset);
                int payloadSize = (int) (fileSize - payloadOffset);
                ByteBuffer payloadBuf = ByteBuffer.allocate(payloadSize);
                channel.read(payloadBuf);

                Files.setLastModifiedTime(targetPath, FileTime.fromMillis(now));
                return Optional.of(payloadBuf.array());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves string payload for the key.
     */
    public Optional<String> getString(String key) throws IOException {
        return get(key).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Removes the cache entry for the given key.
     *
     * @param key cache key to remove
     * @return true if an entry existed and was removed
     * @throws IOException if deletion fails
     */
    public boolean remove(String key) throws IOException {
        Objects.requireNonNull(key, "key must not be null");
        Path targetPath = pathForKey(key);

        lock.writeLock().lock();
        try {
            return Files.deleteIfExists(targetPath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Evicts oldest accessed cache entries until the total directory size is within maxSizeBytes.
     *
     * @param maxSizeBytes maximum permitted storage size in bytes
     * @return number of entries evicted
     * @throws IOException if directory scan or eviction fails
     */
    public int evictIfExceeds(long maxSizeBytes) throws IOException {
        lock.writeLock().lock();
        try {
            record EntryInfo(Path path, long size, long lastAccess) {}
            List<EntryInfo> entries = new ArrayList<>();
            long totalSize = 0;

            try (Stream<Path> stream = Files.list(rootDir)) {
                List<Path> files = stream.filter(p -> p.toString().endsWith(".cache")).toList();
                for (Path p : files) {
                    try {
                        long size = Files.size(p);
                        long accessTime = Files.getLastModifiedTime(p).toMillis();
                        entries.add(new EntryInfo(p, size, accessTime));
                        totalSize += size;
                    } catch (IOException ignored) {
                    }
                }
            }

            if (totalSize <= maxSizeBytes) {
                return 0;
            }

            // Sort by last access ascending (oldest accessed first)
            entries.sort(Comparator.comparingLong(EntryInfo::lastAccess));

            int evictedCount = 0;
            for (EntryInfo entry : entries) {
                if (totalSize <= maxSizeBytes) {
                    break;
                }
                if (Files.deleteIfExists(entry.path())) {
                    totalSize -= entry.size();
                    evictedCount++;
                }
            }
            return evictedCount;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Scans and purges all expired cache files from the directory.
     *
     * @return number of expired entries purged
     * @throws IOException if directory scan fails
     */
    public int cleanExpired() throws IOException {
        lock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            int purged = 0;

            try (Stream<Path> stream = Files.list(rootDir)) {
                List<Path> files = stream.filter(p -> p.toString().endsWith(".cache")).toList();
                for (Path p : files) {
                    try (FileChannel channel = FileChannel.open(p, StandardOpenOption.READ)) {
                        if (channel.size() >= HEADER_FIXED_SIZE) {
                            ByteBuffer buf = ByteBuffer.allocate(HEADER_FIXED_SIZE);
                            channel.read(buf);
                            buf.flip();
                            int magic = buf.getInt();
                            short ver = buf.getShort();
                            long expireAt = buf.getLong();
                            if (magic == MAGIC && expireAt > 0 && now > expireAt) {
                                channel.close();
                                if (Files.deleteIfExists(p)) {
                                    purged++;
                                }
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
            return purged;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears all cache files in the directory.
     */
    public void clear() throws IOException {
        lock.writeLock().lock();
        try {
            try (Stream<Path> stream = Files.list(rootDir)) {
                for (Path p : stream.filter(p -> p.toString().endsWith(".cache")).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path rootDir() {
        return rootDir;
    }

    @Override
    public void close() {
        // No persistent resources to close
    }

    private Path pathForKey(String key) {
        return rootDir.resolve(sha256Hex(key) + ".cache");
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm must be available", e);
        }
    }
}
