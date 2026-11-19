package com.msp.shardrouter;

import java.util.Objects;

/**
 * Physical sharding topology describing how global shard indices map to datasource names
 * and physical table suffixes (e.g. {@code ds_02}, {@code orders_007}).
 */
public record ShardTopology(
        String logicalTable,
        String dataSourcePrefix,
        int dataSourceCount,
        int tablesPerDataSource,
        int dataSourceDigits,
        int tableDigits,
        TableNumberingMode numberingMode
) {
    public enum TableNumberingMode {
        /** Table suffix uses global shard index (e.g., orders_000 .. orders_015). */
        GLOBAL,
        /** Table suffix restarts at 0 within each datasource (e.g., ds_00.orders_00 .. ds_00.orders_03). */
        PER_DATASOURCE
    }

    public ShardTopology {
        if (logicalTable == null || logicalTable.isBlank()) {
            throw new IllegalArgumentException("logicalTable must not be blank");
        }
        if (dataSourcePrefix == null || dataSourcePrefix.isBlank()) {
            throw new IllegalArgumentException("dataSourcePrefix must not be blank");
        }
        if (dataSourceCount <= 0) {
            throw new IllegalArgumentException("dataSourceCount must be > 0");
        }
        if (tablesPerDataSource <= 0) {
            throw new IllegalArgumentException("tablesPerDataSource must be > 0");
        }
        if (dataSourceDigits < 1) {
            throw new IllegalArgumentException("dataSourceDigits must be >= 1");
        }
        if (tableDigits < 1) {
            throw new IllegalArgumentException("tableDigits must be >= 1");
        }
        Objects.requireNonNull(numberingMode, "numberingMode must not be null");
    }

    public int totalShards() {
        return Math.multiplyExact(dataSourceCount, tablesPerDataSource);
    }

    /**
     * Resolves a {@code globalShardIndex} in {@code [0, totalShards())} to a {@link ShardRoute}.
     */
    public ShardRoute resolve(int globalShardIndex) {
        int total = totalShards();
        if (globalShardIndex < 0 || globalShardIndex >= total) {
            throw new IllegalArgumentException(
                    "globalShardIndex out of bounds: " + globalShardIndex + " (totalShards=" + total + ")");
        }
        int dbIndex = globalShardIndex / tablesPerDataSource;
        int localTableIndex = globalShardIndex % tablesPerDataSource;
        int tableSuffixNumber = (numberingMode == TableNumberingMode.GLOBAL) ? globalShardIndex : localTableIndex;

        String dsName = formatWithPadding(dataSourcePrefix, dbIndex, dataSourceDigits);
        String tableName = formatWithPadding(logicalTable + "_", tableSuffixNumber, tableDigits);
        return new ShardRoute(dsName, tableName, logicalTable, dbIndex, tableSuffixNumber, globalShardIndex);
    }

    private static String formatWithPadding(String prefix, int index, int digits) {
        String num = Integer.toString(index);
        if (num.length() >= digits) {
            return prefix + num;
        }
        return prefix + "0".repeat(digits - num.length()) + num;
    }

    public static Builder builder(String logicalTable) {
        return new Builder(logicalTable);
    }

    public static final class Builder {
        private final String logicalTable;
        private String dataSourcePrefix = "ds_";
        private int dataSourceCount = 1;
        private int tablesPerDataSource = 1;
        private int dataSourceDigits = 2;
        private int tableDigits = 3;
        private TableNumberingMode numberingMode = TableNumberingMode.GLOBAL;

        private Builder(String logicalTable) {
            this.logicalTable = logicalTable;
        }

        public Builder dataSourcePrefix(String prefix) {
            this.dataSourcePrefix = prefix;
            return this;
        }

        public Builder dataSourceCount(int count) {
            this.dataSourceCount = count;
            return this;
        }

        public Builder tablesPerDataSource(int count) {
            this.tablesPerDataSource = count;
            return this;
        }

        public Builder dataSourceDigits(int digits) {
            this.dataSourceDigits = digits;
            return this;
        }

        public Builder tableDigits(int digits) {
            this.tableDigits = digits;
            return this;
        }

        public Builder numberingMode(TableNumberingMode mode) {
            this.numberingMode = mode;
            return this;
        }

        public ShardTopology build() {
            return new ShardTopology(
                    logicalTable,
                    dataSourcePrefix,
                    dataSourceCount,
                    tablesPerDataSource,
                    dataSourceDigits,
                    tableDigits,
                    numberingMode
            );
        }
    }
}
