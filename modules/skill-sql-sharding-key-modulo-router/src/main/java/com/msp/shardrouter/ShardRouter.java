package com.msp.shardrouter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-safe, immutable database/table sharding router supporting single-key routing,
 * scatter-gather batch grouping, and numeric range routing.
 */
public final class ShardRouter {

    private final ShardTopology topology;
    private final ShardStrategy strategy;

    public ShardRouter(ShardTopology topology, ShardStrategy strategy) {
        this.topology = Objects.requireNonNull(topology, "topology must not be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    public ShardTopology topology() {
        return topology;
    }

    public ShardStrategy strategy() {
        return strategy;
    }

    public ShardRoute route(ShardingKey key) {
        Objects.requireNonNull(key, "key must not be null");
        int shardIndex = strategy.selectShard(key, topology.totalShards());
        return topology.resolve(shardIndex);
    }

    public ShardRoute route(long key) {
        return route(ShardingKey.of(key));
    }

    public ShardRoute route(String key) {
        return route(ShardingKey.of(key));
    }

    public ShardRoute route(UUID key) {
        return route(ShardingKey.of(key));
    }

    /**
     * Groups a batch of sharding keys by their target {@link ShardRoute} for scatter-gather execution.
     * Preserves key order within each route bucket.
     */
    public Map<ShardRoute, List<ShardingKey>> routeBatch(Collection<? extends ShardingKey> keys) {
        Objects.requireNonNull(keys, "keys must not be null");
        Map<ShardRoute, List<ShardingKey>> grouped = new LinkedHashMap<>();
        for (ShardingKey key : keys) {
            ShardRoute route = route(key);
            grouped.computeIfAbsent(route, k -> new ArrayList<>()).add(key);
        }
        return grouped;
    }

    /**
     * Resolves all distinct {@link ShardRoute}s intersected by the numeric range {@code [inclusiveStart, exclusiveEnd)}.
     * Uses range partition pruning for {@link ShardStrategy.RangeStrategy}, modulo span calculation for
     * {@link ShardStrategy.ModuloStrategy}, and full scatter for consistent hashing.
     */
    public List<ShardRoute> routeRange(long inclusiveStart, long exclusiveEnd) {
        if (inclusiveStart >= exclusiveEnd) {
            throw new IllegalArgumentException(
                    "inclusiveStart (" + inclusiveStart + ") must be < exclusiveEnd (" + exclusiveEnd + ")");
        }
        int total = topology.totalShards();
        return switch (strategy) {
            case ShardStrategy.RangeStrategy rs -> {
                Set<ShardRoute> routes = new LinkedHashSet<>();
                for (ShardStrategy.ShardRange r : rs.ranges()) {
                    if (r.exclusiveEnd() > inclusiveStart && r.inclusiveStart() < exclusiveEnd) {
                        routes.add(topology.resolve(r.shardIndex()));
                    }
                }
                yield List.copyOf(routes);
            }
            case ShardStrategy.ModuloStrategy ms -> {
                long span = exclusiveEnd - inclusiveStart;
                if (span < 0 || span >= total) {
                    yield allRoutes();
                }
                Set<ShardRoute> routes = new LinkedHashSet<>();
                for (long k = inclusiveStart; k < exclusiveEnd; k++) {
                    routes.add(route(k));
                }
                yield List.copyOf(routes);
            }
            case ShardStrategy.ConsistentHashStrategy ignored -> allRoutes();
        };
    }

    /**
     * Returns all physical shard routes in global shard index order (for full scatter-gather queries).
     */
    public List<ShardRoute> allRoutes() {
        int total = topology.totalShards();
        List<ShardRoute> routes = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            routes.add(topology.resolve(i));
        }
        return List.copyOf(routes);
    }
}
