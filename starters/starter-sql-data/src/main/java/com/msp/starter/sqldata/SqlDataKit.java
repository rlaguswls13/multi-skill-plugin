package com.msp.starter.sqldata;

import com.msp.shardrouter.ShardRoute;
import com.msp.shardrouter.ShardRouter;
import com.msp.shardrouter.ShardStrategy;
import com.msp.shardrouter.ShardTopology;
import com.msp.sql.DeleteBuilder;
import com.msp.sql.InsertBuilder;
import com.msp.sql.SelectBuilder;
import com.msp.sql.SqlBuilders;
import com.msp.sql.UpdateBuilder;
import com.msp.sqlinjection.SqlInjectionDetection;
import com.msp.sqlinjection.SqlInjectionDetector;
import com.msp.sqljson.JsonPath;
import com.msp.sqljson.SqlFragment;
import com.msp.sqljson.SqlJsonDialect;
import com.msp.sqljson.SqlJsonPredicateBuilder;
import com.msp.sqlsanitizer.SqlDialect;
import com.msp.sqlsanitizer.SqlIdentifierSanitizer;
import com.msp.sqlseek.KeysetSeekBuilder;
import com.msp.sqlseek.NavigationDirection;
import com.msp.sqlseek.SeekQuerySegment;
import com.msp.sqlseek.SortSpec;

import java.util.List;

/**
 * SQL 쿼리 빌더, 식별자 정제기, SQL Injection 탐지기, 키셋(Seek) 페이징,
 * DB/테이블 샤딩 라우터, 멀티 방언 JSON 경로 조건 빌더를 통합 제공하는 스타터 파사드.
 */
public final class SqlDataKit {

    private SqlDataKit() {
    }

    // 1. Fluent Parameterized SQL Builder
    public static SelectBuilder select(String... columns) {
        return SqlBuilders.select(columns);
    }

    public static SelectBuilder selectDistinct(String... columns) {
        return SqlBuilders.selectDistinct(columns);
    }

    public static InsertBuilder insertInto(String table) {
        return SqlBuilders.insertInto(table);
    }

    public static UpdateBuilder update(String table) {
        return SqlBuilders.update(table);
    }

    public static DeleteBuilder deleteFrom(String table) {
        return SqlBuilders.deleteFrom(table);
    }

    // 2. SQL Identifier Sanitizer
    public static boolean isValidIdentifier(String identifier) {
        return SqlIdentifierSanitizer.isValidIdentifier(identifier);
    }

    public static boolean isReservedKeyword(String word) {
        return SqlIdentifierSanitizer.isReservedKeyword(word);
    }

    public static String sanitizeIdentifier(String identifier, SqlDialect dialect) {
        return SqlIdentifierSanitizer.sanitize(identifier, dialect);
    }

    public static String sanitizeQualifiedIdentifier(String qualifiedName, SqlDialect dialect) {
        return SqlIdentifierSanitizer.sanitizeQualified(qualifiedName, dialect);
    }

    // 3. SQL Injection Detector
    public static SqlInjectionDetection detectSqlInjection(String input) {
        return SqlInjectionDetector.detect(input);
    }

    public static boolean isSqlSafe(String input) {
        return SqlInjectionDetector.isSafe(input);
    }

    public static void assertSqlSafe(String input) {
        SqlInjectionDetector.assertSafe(input);
    }

    // 4. Keyset / Seek Pagination Builder
    public static KeysetSeekBuilder.Builder keysetSeek() {
        return KeysetSeekBuilder.builder();
    }

    public static SeekQuerySegment buildKeysetSeek(
            List<SortSpec> sortSpecs,
            List<?> cursorValues,
            int pageSize,
            NavigationDirection direction
    ) {
        return KeysetSeekBuilder.builder()
                .sortSpecs(sortSpecs)
                .cursorValues(cursorValues)
                .pageSize(pageSize)
                .navigationDirection(direction)
                .build();
    }

    // 5. Sharding Key Router
    public static ShardRouter moduloRouter(String logicalTable, int dataSourceCount, int tablesPerDataSource) {
        ShardTopology topology = ShardTopology.builder(logicalTable)
                .dataSourceCount(dataSourceCount)
                .tablesPerDataSource(tablesPerDataSource)
                .build();
        return new ShardRouter(topology, ShardStrategy.modulo());
    }

    public static ShardRouter consistentHashRouter(String logicalTable, int dataSourceCount, int tablesPerDataSource) {
        ShardTopology topology = ShardTopology.builder(logicalTable)
                .dataSourceCount(dataSourceCount)
                .tablesPerDataSource(tablesPerDataSource)
                .build();
        return new ShardRouter(topology, ShardStrategy.consistentHash(topology.totalShards()));
    }

    public static ShardRoute routeModulo(
            String logicalTable,
            int dataSourceCount,
            int tablesPerDataSource,
            long shardingKey
    ) {
        return moduloRouter(logicalTable, dataSourceCount, tablesPerDataSource).route(shardingKey);
    }

    // 6. Multi-Dialect SQL JSON Path Predicate Builder
    public static SqlJsonPredicateBuilder jsonPredicates(SqlJsonDialect dialect) {
        return SqlJsonPredicateBuilder.forDialect(dialect);
    }

    public static JsonPath jsonPath(String expression) {
        return JsonPath.parse(expression);
    }

    public static SqlFragment jsonEquals(
            SqlJsonDialect dialect,
            String column,
            String pathExpression,
            Object value
    ) {
        return SqlJsonPredicateBuilder.forDialect(dialect).eq(column, JsonPath.parse(pathExpression), value);
    }
}
