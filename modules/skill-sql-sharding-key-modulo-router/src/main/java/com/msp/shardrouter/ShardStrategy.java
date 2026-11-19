package com.msp.shardrouter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Strategy that maps a {@link ShardingKey} to a global shard index in {@code [0, totalShards)}.
 */
public sealed interface ShardStrategy permits
        ShardStrategy.ModuloStrategy,
        ShardStrategy.ConsistentHashStrategy,
        ShardStrategy.RangeStrategy {

    /**
     * Resolves the global shard index for the given key.
     */
    int selectShard(ShardingKey key, int totalShards);

    static ModuloStrategy modulo() {
        return new ModuloStrategy();
    }

    static ConsistentHashStrategy consistentHash(int totalShards, int virtualNodesPerShard) {
        return new ConsistentHashStrategy(totalShards, virtualNodesPerShard);
    }

    static ConsistentHashStrategy consistentHash(int totalShards) {
        return new ConsistentHashStrategy(totalShards, 128);
    }

    static RangeStrategy range(List<ShardRange> ranges) {
        return new RangeStrategy(ranges);
    }

    /**
     * Modulo strategy: computes {@code Math.floorMod(longValue, totalShards)} directly for {@link ShardingKey.LongKey}
     * and {@code Math.floorMod(key.hash64(), totalShards)} for {@code StringKey}/{@code UuidKey}.
     */
    final class ModuloStrategy implements ShardStrategy {
        @Override
        public int selectShard(ShardingKey key, int totalShards) {
            Objects.requireNonNull(key, "key must not be null");
            if (totalShards <= 0) {
                throw new IllegalArgumentException("totalShards must be > 0");
            }
            return switch (key) {
                case ShardingKey.LongKey lk -> (int) Math.floorMod(lk.value(), (long) totalShards);
                case ShardingKey.StringKey sk -> (int) Math.floorMod(sk.hash64(), (long) totalShards);
                case ShardingKey.UuidKey uk -> (int) Math.floorMod(uk.hash64(), (long) totalShards);
            };
        }
    }

    /**
     * Consistent Hashing ring with virtual nodes for balanced distribution and minimal key remapping.
     */
    final class ConsistentHashStrategy implements ShardStrategy {
        private final int configuredShards;
        private final int virtualNodesPerShard;
        private final NavigableMap<Long, Integer> ring;

        public ConsistentHashStrategy(int totalShards, int virtualNodesPerShard) {
            if (totalShards <= 0) {
                throw new IllegalArgumentException("totalShards must be > 0");
            }
            if (virtualNodesPerShard <= 0) {
                throw new IllegalArgumentException("virtualNodesPerShard must be > 0");
            }
            this.configuredShards = totalShards;
            this.virtualNodesPerShard = virtualNodesPerShard;
            TreeMap<Long, Integer> builtRing = new TreeMap<>();
            for (int shard = 0; shard < totalShards; shard++) {
                for (int vn = 0; vn < virtualNodesPerShard; vn++) {
                    long position = sha256Hash64("shard-" + shard + "#vn-" + vn);
                    builtRing.put(position, shard);
                }
            }
            this.ring = builtRing;
        }

        public int configuredShards() {
            return configuredShards;
        }

        public int virtualNodesPerShard() {
            return virtualNodesPerShard;
        }

        @Override
        public int selectShard(ShardingKey key, int totalShards) {
            Objects.requireNonNull(key, "key must not be null");
            if (totalShards != configuredShards) {
                throw new IllegalArgumentException(
                        "ConsistentHashStrategy configured for " + configuredShards + " shards, got " + totalShards);
            }
            long hash = key.hash64();
            Map.Entry<Long, Integer> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                entry = ring.firstEntry();
            }
            return entry.getValue();
        }

        private static long sha256Hash64(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
                long h = 0;
                for (int i = 0; i < 8; i++) {
                    h = (h << 8) | (digest[i] & 0xFFL);
                }
                return h;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }
    }

    /**
     * Half-open numeric range {@code [inclusiveStart, exclusiveEnd)} mapped to a global shard index.
     */
    record ShardRange(long inclusiveStart, long exclusiveEnd, int shardIndex) {
        public ShardRange {
            if (inclusiveStart >= exclusiveEnd) {
                throw new IllegalArgumentException(
                        "inclusiveStart (" + inclusiveStart + ") must be < exclusiveEnd (" + exclusiveEnd + ")");
            }
            if (shardIndex < 0) {
                throw new IllegalArgumentException("shardIndex must be >= 0");
            }
        }

        public boolean contains(long value) {
            return value >= inclusiveStart && value < exclusiveEnd;
        }
    }

    /**
     * Range-based routing strategy over sorted non-overlapping {@link ShardRange} partitions.
     */
    final class RangeStrategy implements ShardStrategy {
        private final NavigableMap<Long, ShardRange> rangeMap;

        public RangeStrategy(List<ShardRange> ranges) {
            Objects.requireNonNull(ranges, "ranges must not be null");
            if (ranges.isEmpty()) {
                throw new IllegalArgumentException("ranges must not be empty");
            }
            List<ShardRange> sorted = new ArrayList<>(ranges);
            sorted.sort(Comparator.comparingLong(ShardRange::inclusiveStart));

            TreeMap<Long, ShardRange> map = new TreeMap<>();
            ShardRange prev = null;
            for (ShardRange range : sorted) {
                Objects.requireNonNull(range, "range element must not be null");
                if (prev != null && range.inclusiveStart() < prev.exclusiveEnd()) {
                    throw new IllegalArgumentException(
                            "Overlapping shard ranges: " + prev + " and " + range);
                }
                map.put(range.inclusiveStart(), range);
                prev = range;
            }
            this.rangeMap = map;
        }

        public List<ShardRange> ranges() {
            return List.copyOf(rangeMap.values());
        }

        @Override
        public int selectShard(ShardingKey key, int totalShards) {
            Objects.requireNonNull(key, "key must not be null");
            if (!(key instanceof ShardingKey.LongKey(long value))) {
                throw new IllegalArgumentException(
                        "RangeStrategy requires ShardingKey.LongKey, got: " + key.getClass().getSimpleName());
            }
            Map.Entry<Long, ShardRange> floor = rangeMap.floorEntry(value);
            if (floor == null || !floor.getValue().contains(value)) {
                throw new IllegalArgumentException("Key " + value + " does not fall into any configured ShardRange");
            }
            int shardIndex = floor.getValue().shardIndex();
            if (shardIndex >= totalShards) {
                throw new IllegalArgumentException(
                        "Mapped shardIndex " + shardIndex + " exceeds totalShards " + totalShards);
            }
            return shardIndex;
        }
    }
}
