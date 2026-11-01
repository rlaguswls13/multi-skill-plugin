package com.msp.clausesplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlClauseSplitterTest {

    @Test
    @DisplayName("Parses standard full SELECT query with all clauses")
    void testStandardSelectQuery() {
        String sql = """
                SELECT u.id, u.name, count(o.id) as order_count
                FROM users u
                LEFT JOIN orders o ON u.id = o.user_id
                WHERE u.status = 'ACTIVE'
                GROUP BY u.id, u.name
                HAVING count(o.id) > 5
                ORDER BY order_count DESC
                LIMIT 20
                OFFSET 40
                """;

        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(8, result.size());
        assertTrue(result.hasClause(ClauseType.SELECT));
        assertTrue(result.hasClause(ClauseType.FROM));
        assertTrue(result.hasClause(ClauseType.WHERE));
        assertTrue(result.hasClause(ClauseType.GROUP_BY));
        assertTrue(result.hasClause(ClauseType.HAVING));
        assertTrue(result.hasClause(ClauseType.ORDER_BY));
        assertTrue(result.hasClause(ClauseType.LIMIT));
        assertTrue(result.hasClause(ClauseType.OFFSET));

        assertEquals("u.id, u.name, count(o.id) as order_count", result.getClauseBody(ClauseType.SELECT).orElseThrow());
        assertTrue(result.getClauseBody(ClauseType.FROM).orElseThrow().startsWith("users u"));
        assertEquals("u.status = 'ACTIVE'", result.getClauseBody(ClauseType.WHERE).orElseThrow());
        assertEquals("u.id, u.name", result.getClauseBody(ClauseType.GROUP_BY).orElseThrow());
        assertEquals("count(o.id) > 5", result.getClauseBody(ClauseType.HAVING).orElseThrow());
        assertEquals("order_count DESC", result.getClauseBody(ClauseType.ORDER_BY).orElseThrow());
        assertEquals("20", result.getClauseBody(ClauseType.LIMIT).orElseThrow());
        assertEquals("40", result.getClauseBody(ClauseType.OFFSET).orElseThrow());
    }

    @Test
    @DisplayName("Respects subqueries inside parentheses and does not split them")
    void testSubqueryRespect() {
        String sql = "SELECT id, (SELECT count(*) FROM orders o WHERE o.user_id = u.id) AS cnt "
                   + "FROM users u "
                   + "WHERE u.id IN (SELECT user_id FROM vip_list WHERE active = 1)";

        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(3, result.size());
        assertEquals(ClauseType.SELECT, result.clauses().get(0).type());
        assertEquals(ClauseType.FROM, result.clauses().get(1).type());
        assertEquals(ClauseType.WHERE, result.clauses().get(2).type());

        String selectBody = result.getClauseBody(ClauseType.SELECT).orElseThrow();
        assertTrue(selectBody.contains("(SELECT count(*) FROM orders o WHERE o.user_id = u.id)"));

        String whereBody = result.getClauseBody(ClauseType.WHERE).orElseThrow();
        assertTrue(whereBody.contains("(SELECT user_id FROM vip_list WHERE active = 1)"));
    }

    @Test
    @DisplayName("Ignores keywords inside single and double quotes")
    void testQuotedStringsIgnored() {
        String sql = "SELECT 'FROM here' AS title, \"WHERE\" AS col FROM my_table WHERE note = 'ORDER BY something' AND text = \"GROUP BY\"";

        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(3, result.size());
        assertTrue(result.hasClause(ClauseType.SELECT));
        assertTrue(result.hasClause(ClauseType.FROM));
        assertTrue(result.hasClause(ClauseType.WHERE));
        assertFalse(result.hasClause(ClauseType.ORDER_BY));
        assertFalse(result.hasClause(ClauseType.GROUP_BY));
    }

    @Test
    @DisplayName("Ignores keywords inside SQL comments")
    void testCommentsIgnored() {
        String sql = """
                SELECT id, name
                FROM members -- WHERE deleted = 0
                /* GROUP BY id
                   HAVING count(*) > 1 */
                WHERE active = 1
                """;

        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(3, result.size());
        assertTrue(result.hasClause(ClauseType.SELECT));
        assertTrue(result.hasClause(ClauseType.FROM));
        assertTrue(result.hasClause(ClauseType.WHERE));
        assertFalse(result.hasClause(ClauseType.GROUP_BY));
        assertFalse(result.hasClause(ClauseType.HAVING));
    }

    @Test
    @DisplayName("Preserves original casing of keywords")
    void testKeywordCasingPreserved() {
        String sql = "select id from users where active = 1 order by id desc";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(4, result.size());
        assertEquals("select", result.clauses().get(0).keyword());
        assertEquals("from", result.clauses().get(1).keyword());
        assertEquals("where", result.clauses().get(2).keyword());
        assertEquals("order by", result.clauses().get(3).keyword());
    }

    @Test
    @DisplayName("Appends condition to existing WHERE clause")
    void testAppendConditionToExistingWhere() {
        String sql = "SELECT * FROM products WHERE price > 100 ORDER BY price";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        SqlSplitResult updated = result.appendConditionToWhere("stock > 0", "AND");
        assertEquals("price > 100 AND (stock > 0)", updated.getClauseBody(ClauseType.WHERE).orElseThrow());

        String generated = updated.toInlineSql();
        assertTrue(generated.contains("WHERE price > 100 AND (stock > 0)"));
    }

    @Test
    @DisplayName("Inserts WHERE clause when not originally present")
    void testInsertWhereWhenMissing() {
        String sql = "SELECT id, title FROM articles ORDER BY created_at DESC LIMIT 10";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertFalse(result.hasClause(ClauseType.WHERE));

        SqlSplitResult updated = result.appendConditionToWhere("published = true");
        assertTrue(updated.hasClause(ClauseType.WHERE));
        assertEquals("(published = true)", updated.getClauseBody(ClauseType.WHERE).orElseThrow());

        // Verify order: SELECT -> FROM -> WHERE -> ORDER BY -> LIMIT
        List<ClauseType> order = updated.clauses().stream().map(SqlClause::type).toList();
        assertEquals(List.of(ClauseType.SELECT, ClauseType.FROM, ClauseType.WHERE, ClauseType.ORDER_BY, ClauseType.LIMIT), order);
    }

    @Test
    @DisplayName("Replaces clause body")
    void testReplaceClause() {
        String sql = "SELECT * FROM users WHERE status = 'PENDING'";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        SqlSplitResult updated = result.replaceClause(ClauseType.WHERE, "status = 'APPROVED' AND verified = 1");
        assertEquals("status = 'APPROVED' AND verified = 1", updated.getClauseBody(ClauseType.WHERE).orElseThrow());
    }

    @Test
    @DisplayName("Removes clause")
    void testRemoveClause() {
        String sql = "SELECT * FROM users WHERE active = 1 ORDER BY id";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        SqlSplitResult withoutWhere = result.removeClause(ClauseType.WHERE);
        assertFalse(withoutWhere.hasClause(ClauseType.WHERE));
        assertEquals(3, withoutWhere.size());
    }

    @Test
    @DisplayName("Handles UNION and UNION ALL queries")
    void testUnionAndUnionAll() {
        String sql = "SELECT id FROM table_a UNION ALL SELECT id FROM table_b UNION SELECT id FROM table_c";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(8, result.size());
        assertTrue(result.hasClause(ClauseType.UNION_ALL));
        assertTrue(result.hasClause(ClauseType.UNION));
        assertEquals(3, result.getClauses(ClauseType.SELECT).size());
    }

    @Test
    @DisplayName("Handles INSERT INTO and VALUES")
    void testInsertIntoAndValues() {
        String sql = "INSERT INTO users (name, email) VALUES ('Alice', 'alice@test.com')";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(2, result.size());
        assertEquals(ClauseType.INSERT_INTO, result.clauses().get(0).type());
        assertEquals("users (name, email)", result.clauses().get(0).trimmedBody());
        assertEquals(ClauseType.VALUES, result.clauses().get(1).type());
        assertEquals("('Alice', 'alice@test.com')", result.clauses().get(1).trimmedBody());
    }

    @Test
    @DisplayName("Handles UPDATE and SET")
    void testUpdateAndSet() {
        String sql = "UPDATE accounts SET balance = balance + 100 WHERE id = 42;";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(3, result.size());
        assertEquals(ClauseType.UPDATE, result.clauses().get(0).type());
        assertEquals("accounts", result.clauses().get(0).trimmedBody());
        assertEquals(ClauseType.SET, result.clauses().get(1).type());
        assertEquals("balance = balance + 100", result.clauses().get(1).trimmedBody());
        assertEquals(ClauseType.WHERE, result.clauses().get(2).type());
        assertEquals("id = 42", result.clauses().get(2).trimmedBody());
    }

    @Test
    @DisplayName("Handles DELETE FROM")
    void testDeleteFrom() {
        String sql = "DELETE FROM logs WHERE created_at < NOW() - INTERVAL '30 days'";
        SqlSplitResult result = SqlClauseSplitter.split(sql);

        assertEquals(2, result.size());
        assertEquals(ClauseType.DELETE_FROM, result.clauses().get(0).type());
        assertEquals("logs", result.clauses().get(0).trimmedBody());
        assertEquals(ClauseType.WHERE, result.clauses().get(1).type());
    }

    @Test
    @DisplayName("Validates null or empty input")
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> SqlClauseSplitter.split(null));
        assertThrows(IllegalArgumentException.class, () -> SqlClauseSplitter.split("   "));
    }
}
