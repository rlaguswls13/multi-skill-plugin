package com.msp.shardrouter;

import java.util.Objects;

/**
 * Resolved physical sharding route mapping a sharding key to a physical datasource and table.
 */
public record ShardRoute(
        String dataSource,
        String physicalTable,
        String logicalTable,
        int dataSourceIndex,
        int tableIndex,
        int globalShardIndex
) {
    public ShardRoute {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Objects.requireNonNull(physicalTable, "physicalTable must not be null");
        Objects.requireNonNull(logicalTable, "logicalTable must not be null");
        if (dataSourceIndex < 0) {
            throw new IllegalArgumentException("dataSourceIndex must be >= 0");
        }
        if (tableIndex < 0) {
            throw new IllegalArgumentException("tableIndex must be >= 0");
        }
        if (globalShardIndex < 0) {
            throw new IllegalArgumentException("globalShardIndex must be >= 0");
        }
    }

    /**
     * Returns qualified target notation {@code dataSource.physicalTable}.
     */
    public String qualifiedName() {
        return dataSource + "." + physicalTable;
    }
}
