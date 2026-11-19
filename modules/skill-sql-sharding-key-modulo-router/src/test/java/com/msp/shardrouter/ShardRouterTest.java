package com.msp.shardrouter;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShardRouterTest {

    @Test
    void moduloRoutingWithGlobalTableNumbering() {
        ShardTopology topology = ShardTopology.builder("orders")
                .dataSourcePrefix("ds_")
                .dataSourceCount(4)
                .tablesPerDataSource(4)
                .dataSourceDigits(2)
                .tableDigits(3)
                .numberingMode(ShardTopology.TableNumberingMode.GLOBAL)
                .build();

        ShardRouter router = new ShardRouter(topology, ShardStrategy.modulo());

        // 7 % 16 = 7 -> db = 7 / 4 = 1, global table = 7
        ShardRoute route7 = router.route(7L);
        assertEquals("ds_01", route7.dataSource());
        assertEquals("orders_007", route7.physicalTable());
        assertEquals("ds_01.orders_007", route7.qualifiedName());
        assertEquals(1, route7.dataSourceIndex());
        assertEquals(7, route7.tableIndex());
        assertEquals(7, route7.globalShardIndex());

        // 25 % 16 = 9 -> db = 9 / 4 = 2, global table = 9
        ShardRoute route25 = router.route(25L);
        assertEquals("ds_02", route25.dataSource());
        assertEquals("orders_009", route25.physicalTable());

        // Negative key uses floorMod: floorMod(-1, 16) = 15 -> db = 3, table = 15
        ShardRoute routeNeg = router.route(-1L);
        assertEquals("ds_03", routeNeg.dataSource());
        assertEquals("orders_015", routeNeg.physicalTable());
    }

    @Test
    void moduloRoutingWithPerDatasourceTableNumbering() {
        ShardTopology topology = ShardTopology.builder("users")
                .dataSourcePrefix("shard_db_")
                .dataSourceCount(3)
                .tablesPerDataSource(4)
                .dataSourceDigits(2)
                .tableDigits(2)
                .numberingMode(ShardTopology.TableNumberingMode.PER_DATASOURCE)
                .build();

        ShardRouter router = new ShardRouter(topology, ShardStrategy.modulo());

        // 10 % 12 = 10 -> db = 10 / 4 = 2, local table = 10 % 4 = 2
        ShardRoute route = router.route(10L);
        assertEquals("shard_db_02", route.dataSource());
        assertEquals("users_02", route.physicalTable());
        assertEquals(2, route.dataSourceIndex());
        assertEquals(2, route.tableIndex());
        assertEquals(10, route.globalShardIndex());
    }

    @Test
    void moduloRoutingSupportsStringAndUuidKeysDeterministically() {
        ShardTopology topology = ShardTopology.builder("events")
                .dataSourceCount(4)
                .tablesPerDataSource(2)
                .build();
        ShardRouter router = new ShardRouter(topology, ShardStrategy.modulo());

        ShardRoute r1 = router.route("tenant-acme-001");
        ShardRoute r2 = router.route("tenant-acme-001");
        assertEquals(r1, r2);

        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        ShardRoute u1 = router.route(id);
        ShardRoute u2 = router.route(id);
        assertEquals(u1, u2);
    }

    @Test
    void consistentHashDistributesKeysAcrossAllShards() {
        ShardTopology topology = ShardTopology.builder("sessions")
                .dataSourceCount(4)
                .tablesPerDataSource(2)
                .build();
        ShardRouter router = new ShardRouter(topology, ShardStrategy.consistentHash(topology.totalShards(), 150));

        Map<Integer, Integer> counts = new HashMap<>();
        int totalKeys = 10_000;
        for (int i = 0; i < totalKeys; i++) {
            ShardRoute route = router.route("session-" + i);
            counts.merge(route.globalShardIndex(), 1, Integer::sum);
        }

        assertEquals(8, counts.size(), "All 8 shards should receive traffic");
        // Average is 1250; with 150 vnodes each shard should comfortably be within [700, 1900]
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            assertTrue(e.getValue() >= 700 && e.getValue() <= 1900,
                    "Shard " + e.getKey() + " had unbalanced count: " + e.getValue());
        }
    }

    @Test
    void rangeRoutingAndRangePruning() {
        ShardTopology topology = ShardTopology.builder("invoices")
                .dataSourceCount(2)
                .tablesPerDataSource(2)
                .build();

        ShardStrategy rangeStrategy = ShardStrategy.range(List.of(
                new ShardStrategy.ShardRange(0, 1_000, 0),
                new ShardStrategy.ShardRange(1_000, 2_000, 1),
                new ShardStrategy.ShardRange(2_000, 3_000, 2),
                new ShardStrategy.ShardRange(3_000, 4_000, 3)
        ));
        ShardRouter router = new ShardRouter(topology, rangeStrategy);

        assertEquals(0, router.route(0L).globalShardIndex());
        assertEquals(0, router.route(999L).globalShardIndex());
        assertEquals(1, router.route(1_000L).globalShardIndex());
        assertEquals(3, router.route(3_999L).globalShardIndex());

        assertThrows(IllegalArgumentException.class, () -> router.route(4_000L));
        assertThrows(IllegalArgumentException.class, () -> router.route(-5L));
        assertThrows(IllegalArgumentException.class, () -> router.route("non-numeric"));

        // Range [500, 2500) intersects shards 0, 1, 2
        List<ShardRoute> pruned = router.routeRange(500, 2_500);
        assertEquals(3, pruned.size());
        assertEquals(List.of(0, 1, 2), pruned.stream().map(ShardRoute::globalShardIndex).toList());
    }

    @Test
    void rejectsOverlappingRanges() {
        assertThrows(IllegalArgumentException.class, () -> ShardStrategy.range(List.of(
                new ShardStrategy.ShardRange(0, 100, 0),
                new ShardStrategy.ShardRange(50, 200, 1)
        )));
    }

    @Test
    void scatterGatherRouteBatchGroupsKeysByPhysicalRoute() {
        ShardTopology topology = ShardTopology.builder("orders")
                .dataSourceCount(2)
                .tablesPerDataSource(2)
                .build();
        ShardRouter router = new ShardRouter(topology, ShardStrategy.modulo());

        List<ShardingKey> keys = List.of(
                ShardingKey.of(1L),
                ShardingKey.of(5L),
                ShardingKey.of(2L),
                ShardingKey.of(9L),
                ShardingKey.of(6L)
        );
        Map<ShardRoute, List<ShardingKey>> grouped = router.routeBatch(keys);

        assertEquals(2, grouped.size());
        ShardRoute shard1 = topology.resolve(1);
        ShardRoute shard2 = topology.resolve(2);
        assertEquals(List.of(ShardingKey.of(1L), ShardingKey.of(5L), ShardingKey.of(9L)), grouped.get(shard1));
        assertEquals(List.of(ShardingKey.of(2L), ShardingKey.of(6L)), grouped.get(shard2));
    }

    @Test
    void moduloRangeRouteReturnsExactSubsetOrFullScatter() {
        ShardTopology topology = ShardTopology.builder("orders")
                .dataSourceCount(2)
                .tablesPerDataSource(4)
                .build();
        ShardRouter router = new ShardRouter(topology, ShardStrategy.modulo());

        // Span of 3 keys -> 3 routes
        List<ShardRoute> small = router.routeRange(10, 13);
        assertEquals(3, small.size());

        // Span >= totalShards (8) -> all 8 routes
        List<ShardRoute> full = router.routeRange(0, 100);
        assertEquals(8, full.size());
    }
}
