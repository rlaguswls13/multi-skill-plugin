package com.msp.sqlvisitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sql AST Visitor & Parser Tests")
class SqlAstVisitorTest {

    @Test
    @DisplayName("AST Builder creates well-formed SelectQueryNode")
    void testAstBuilder() {
        SelectQueryNode query = SelectQueryNode.builder()
                .select("u.id", "u.username", "u.email")
                .from("users", "u")
                .join(JoinType.LEFT, new TableRefNode("dbo", "orders", "o"),
                        new BinaryOpNode(new ColumnRefNode("u", "id"), "=", new ColumnRefNode("o", "user_id")))
                .where(new BinaryOpNode(new ColumnRefNode("u", "status"), "=", LiteralNode.ofString("ACTIVE")))
                .orderBy("u.created_at", SortDirection.DESC)
                .limit(20)
                .offset(40)
                .build();

        assertNotNull(query);
        assertEquals(3, query.projections().size());
        assertEquals("users", query.fromTable().tableName());
        assertEquals("u", query.fromTable().alias());
        assertEquals(1, query.joins().size());
        assertEquals("dbo.orders", query.joins().get(0).table().getQualifiedName());
        assertNotNull(query.whereClause());
        assertEquals(20, query.limit());
        assertEquals(40, query.offset());
    }

    @Test
    @DisplayName("TableCollectorVisitor extracts all tables and aliases")
    void testTableCollectorVisitor() {
        String sql = "SELECT u.id, o.total FROM app.users u LEFT JOIN app.orders o ON u.id = o.user_id WHERE u.active = 1";
        SelectQueryNode ast = SimpleSqlAstParser.parse(sql);

        TableCollectorVisitor visitor = new TableCollectorVisitor();
        ast.accept(visitor);

        Set<String> tableNames = visitor.getTableNames();
        assertTrue(tableNames.contains("app.users"));
        assertTrue(tableNames.contains("app.orders"));

        assertEquals("app.users", visitor.getAliasToTableMap().get("u"));
        assertEquals("app.orders", visitor.getAliasToTableMap().get("o"));
        assertEquals(2, visitor.getTables().size());
    }

    @Test
    @DisplayName("ColumnCollectorVisitor collects projected and filter columns")
    void testColumnCollectorVisitor() {
        String sql = "SELECT u.name, u.age, COUNT(*) FROM users u " +
                "INNER JOIN profiles p ON u.id = p.user_id " +
                "WHERE u.age >= 18 AND p.verified = 1 " +
                "GROUP BY u.name, u.age " +
                "ORDER BY u.age DESC";

        SelectQueryNode ast = SimpleSqlAstParser.parse(sql);

        ColumnCollectorVisitor visitor = new ColumnCollectorVisitor();
        ast.accept(visitor);

        Set<String> allCols = visitor.getAllColumnNames();
        assertTrue(allCols.contains("u.name"));
        assertTrue(allCols.contains("u.age"));
        assertTrue(allCols.contains("u.id"));
        assertTrue(allCols.contains("p.user_id"));
        assertTrue(allCols.contains("p.verified"));

        assertEquals(2, visitor.getProjectedColumns().size());
        assertTrue(visitor.getFilterColumns().size() >= 4); // join conditions + where predicates
        assertEquals(2, visitor.getGroupByColumns().size());
        assertEquals(1, visitor.getOrderByColumns().size());
    }

    @Test
    @DisplayName("SqlInjectionGuardVisitor detects tautologies and OR bypasses")
    void testSqlInjectionGuardDetectsTautologies() {
        // Tautology: '1' = '1'
        String sqlInjection1 = "SELECT id FROM users WHERE username = 'admin' OR '1' = '1'";
        SelectQueryNode ast1 = SimpleSqlAstParser.parse(sqlInjection1);

        SqlInjectionGuardVisitor guard1 = new SqlInjectionGuardVisitor();
        ast1.accept(guard1);

        assertFalse(guard1.isSafe());
        assertTrue(guard1.getAlerts().stream().anyMatch(a -> a.ruleId().equals("OR_TAUTOLOGY_BYPASS")));
        assertTrue(guard1.getAlerts().stream().anyMatch(a -> a.ruleId().equals("LITERAL_TAUTOLOGY_EQUAL")));

        // Tautology: 1 = 1
        String sqlInjection2 = "SELECT id FROM accounts WHERE balance > 0 OR 1 = 1";
        SelectQueryNode ast2 = SimpleSqlAstParser.parse(sqlInjection2);
        SqlInjectionGuardVisitor guard2 = new SqlInjectionGuardVisitor();
        ast2.accept(guard2);
        assertFalse(guard2.isSafe());

        // Safe query
        String safeSql = "SELECT id, name FROM users WHERE id = 123 AND status = 'ACTIVE'";
        SelectQueryNode astSafe = SimpleSqlAstParser.parse(safeSql);
        SqlInjectionGuardVisitor guardSafe = new SqlInjectionGuardVisitor();
        astSafe.accept(guardSafe);
        assertTrue(guardSafe.isSafe());
        assertTrue(guardSafe.getAlerts().isEmpty());
    }

    @Test
    @DisplayName("SqlInjectionGuardVisitor detects column self compare and inequality tautology")
    void testSelfCompareAndInequalityTautology() {
        SelectQueryNode selfCompare = SelectQueryNode.builder()
                .select("id")
                .from("users")
                .where(new BinaryOpNode(new ColumnRefNode("id"), "=", new ColumnRefNode("id")))
                .build();

        SqlInjectionGuardVisitor guard = new SqlInjectionGuardVisitor();
        selfCompare.accept(guard);
        assertFalse(guard.isSafe());
        assertTrue(guard.getAlerts().stream().anyMatch(a -> a.ruleId().equals("COLUMN_SELF_COMPARE_TAUTOLOGY")));

        SelectQueryNode inequality = SelectQueryNode.builder()
                .select("id")
                .from("users")
                .where(new BinaryOpNode(LiteralNode.ofNumber(1), "!=", LiteralNode.ofNumber(2)))
                .build();

        SqlInjectionGuardVisitor guard2 = new SqlInjectionGuardVisitor();
        inequality.accept(guard2);
        assertFalse(guard2.isSafe());
        assertTrue(guard2.getAlerts().stream().anyMatch(a -> a.ruleId().equals("LITERAL_TAUTOLOGY_NOT_EQUAL")));
    }

    @Test
    @DisplayName("SqlAstSqlPrinter formats AST back into valid SQL")
    void testSqlAstSqlPrinter() {
        String sql = "SELECT id, name FROM users WHERE id = 100 ORDER BY name ASC LIMIT 10 OFFSET 5";
        SelectQueryNode ast = SimpleSqlAstParser.parse(sql);

        SqlAstSqlPrinter printer = new SqlAstSqlPrinter();
        String generatedSql = ast.accept(printer);

        assertNotNull(generatedSql);
        assertTrue(generatedSql.startsWith("SELECT id, name FROM users"));
        assertTrue(generatedSql.contains("WHERE id = 100"));
        assertTrue(generatedSql.contains("ORDER BY name ASC"));
        assertTrue(generatedSql.contains("LIMIT 10"));
        assertTrue(generatedSql.contains("OFFSET 5"));
    }

    @Test
    @DisplayName("Parser handles IN and NOT IN expressions")
    void testInAndNotInParsing() {
        String sql = "SELECT * FROM products WHERE category_id IN (1, 2, 3) AND status NOT IN ('DELETED', 'ARCHIVED')";
        SelectQueryNode ast = SimpleSqlAstParser.parse(sql);

        assertNotNull(ast.whereClause());
        ColumnCollectorVisitor colVisitor = new ColumnCollectorVisitor();
        ast.accept(colVisitor);

        assertTrue(colVisitor.getAllColumnNames().contains("category_id"));
        assertTrue(colVisitor.getAllColumnNames().contains("status"));
    }
}
