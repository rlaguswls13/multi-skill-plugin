# skill-sql-sharding-key-modulo-router

Zero-dependency Java 21 database & table sharding router supporting **Modulo**, **Consistent Hashing (virtual nodes)**, and **Range** routing strategies, plus scatter-gather batch grouping.

## Architecture Highlights
- **Typed Sharding Keys (`ShardingKey`)**: Sealed interface supporting `LongKey`, `StringKey`, and `UuidKey` with deterministic 64-bit mixing.
- **Flexible Topology (`ShardTopology`)**: Maps global shard indices to physical datasource names (`ds_02`) and physical tables (`orders_007`), supporting both `GLOBAL` and `PER_DATASOURCE` table numbering modes.
- **Pluggable Strategies (`ShardStrategy`)**:
  - `ModuloStrategy`: Exact `Math.floorMod` for numeric keys and 64-bit hash modulo for `String`/`UUID` keys.
  - `ConsistentHashStrategy`: Immutable SHA-256 virtual-node hash ring (`TreeMap`).
  - `RangeStrategy`: Validated non-overlapping half-open numeric ranges with partition-pruned range routing.
- **Scatter-Gather Support**: `routeBatch(...)` groups multi-key queries by target `ShardRoute`, and `routeRange(...)` prunes unnecessary shards.

## Usage Example
```java
import com.msp.shardrouter.*;
import java.util.List;
import java.util.Map;

ShardTopology topology = ShardTopology.builder("orders")
        .dataSourcePrefix("ds_")
        .dataSourceCount(4)
        .tablesPerDataSource(4)
        .dataSourceDigits(2)
        .tableDigits(3)
        .build();

ShardRouter router = new ShardRouter(topology, ShardStrategy.modulo());

ShardRoute route = router.route(25L);
System.out.println(route.qualifiedName()); // ds_02.orders_009

Map<ShardRoute, List<ShardingKey>> batches = router.routeBatch(
        List.of(ShardingKey.of(1L), ShardingKey.of(17L), ShardingKey.of(25L))
);
```
