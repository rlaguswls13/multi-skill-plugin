package com.msp.sqlcte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CteBuilderTest {

    @Test
    @DisplayName("Build standard non-recursive CTE query")
    void testStandardCte() {
        CteQuery query = CteBuilder.with("recent_orders", "SELECT id, user_id, amount FROM orders WHERE created_at >= '2026-01-01'")
                .finalSelect("SELECT user_id, SUM(amount) FROM recent_orders GROUP BY user_id");

        assertFalse(query.isRecursive());
        assertEquals(1, query.standardCtes().size());
        assertEquals(0, query.recursiveCtes().size());

        String sql = query.toSql();
        assertTrue(sql.startsWith("WITH recent_orders AS ("));
        assertTrue(sql.contains("SELECT id, user_id, amount FROM orders"));
        assertTrue(sql.contains("SELECT user_id, SUM(amount) FROM recent_orders"));
    }

    @Test
    @DisplayName("Build multiple chained CTEs")
    void testMultipleCtes() {
        CteQuery query = CteBuilder.create()
                .withCte("dept_avg", List.of("dept_id", "avg_salary"), "SELECT dept_id, AVG(salary) FROM emp GROUP BY dept_id")
                .withCte("high_earners", "SELECT * FROM emp WHERE salary > 100000")
                .finalSelect("SELECT * FROM high_earners h JOIN dept_avg d ON h.dept_id = d.dept_id");

        assertFalse(query.isRecursive());
        assertEquals(2, query.standardCtes().size());
        String sql = query.toSql();
        assertTrue(sql.startsWith("WITH dept_avg (dept_id, avg_salary) AS ("));
        assertTrue(sql.contains("high_earners AS ("));
        assertTrue(sql.contains("SELECT * FROM high_earners h"));
    }

    @Test
    @DisplayName("Build WITH RECURSIVE query for organizational hierarchy")
    void testRecursiveCteBuilder() {
        CteQuery query = CteBuilder.recursive("org_tree", "emp_id", "mgr_id", "name", "depth")
                .anchorSelect("SELECT emp_id, mgr_id, name, 1 FROM employees WHERE mgr_id IS NULL")
                .unionAll()
                .recursiveSelect("SELECT e.emp_id, e.mgr_id, e.name, o.depth + 1 FROM employees e JOIN org_tree o ON e.mgr_id = o.emp_id")
                .finalSelect("SELECT * FROM org_tree ORDER BY depth, emp_id");

        assertTrue(query.isRecursive());
        assertEquals(1, query.recursiveCtes().size());
        String sql = query.toSql();
        assertTrue(sql.startsWith("WITH RECURSIVE org_tree (emp_id, mgr_id, name, depth) AS ("));
        assertTrue(sql.contains("UNION ALL"));
        assertTrue(sql.contains("SELECT * FROM org_tree ORDER BY depth, emp_id"));
    }

    @Test
    @DisplayName("Build recursive CTE with ANSI SQL CYCLE clause")
    void testAnsiCycleClause() {
        CteQuery query = CteBuilder.recursive("graph_path")
                .columns("id", "target_id", "depth")
                .anchorSelect("SELECT id, target_id, 1 FROM edges WHERE id = 10")
                .unionAll()
                .recursiveSelect("SELECT e.id, e.target_id, g.depth + 1 FROM edges e JOIN graph_path g ON e.id = g.target_id")
                .ansiCycle("id", "is_cycle", "cycle_path")
                .finalSelect("SELECT * FROM graph_path WHERE is_cycle = 'N'");

        assertTrue(query.isRecursive());
        String sql = query.toSql();
        assertTrue(sql.contains("CYCLE id SET is_cycle TO 'Y' DEFAULT 'N' USING cycle_path"));
    }

    @Test
    @DisplayName("Build recursive CTE with string path containment cycle prevention")
    void testPathContainmentCycleDetection() {
        CteQuery query = CteBuilder.recursive("cat_tree")
                .columns("id", "parent_id", "path")
                .anchorSelect("SELECT id, parent_id, '/' || CAST(id AS VARCHAR(50)) || '/' FROM categories WHERE parent_id IS NULL")
                .unionAll()
                .recursiveSelect("SELECT c.id, c.parent_id, t.path || CAST(c.id AS VARCHAR(50)) || '/' FROM categories c JOIN cat_tree t ON c.parent_id = t.id")
                .detectCycleByPath("t.path", "c.id")
                .finalSelect("SELECT * FROM cat_tree");

        String sql = query.toSql();
        assertTrue(sql.contains("POSITION('/' || CAST(c.id AS VARCHAR(50)) || '/' IN t.path) = 0"));
    }

    @Test
    @DisplayName("Build recursive CTE with max depth limit")
    void testMaxDepthLimit() {
        CteQuery query = CteBuilder.recursive("levels")
                .columns("node_id", "depth")
                .anchorSelect("SELECT 1, 1")
                .unionAll()
                .recursiveSelect("SELECT node_id + 1, depth + 1 FROM levels WHERE depth < 50")
                .maxDepth("depth", 100)
                .finalSelect("SELECT * FROM levels");

        String sql = query.toSql();
        assertTrue(sql.contains("depth < 100"));
    }

    @Test
    @DisplayName("HierarchyTemplate generates valid org chart query")
    void testHierarchyTemplateOrgChart() {
        CteQuery query = HierarchyTemplate.buildOrgChartQuery(
                new HierarchyTemplate.OrgChartConfig("org_chart", "staff", "id", "supervisor_id", "full_name", "supervisor_id IS NULL", 5)
        );

        assertTrue(query.isRecursive());
        String sql = query.toSql();
        assertTrue(sql.startsWith("WITH RECURSIVE org_chart (id, supervisor_id, full_name, depth, path) AS ("));
        assertTrue(sql.contains("staff"));
        assertTrue(sql.contains("t.depth < 5"));
        assertTrue(sql.contains("ORDER BY depth, id"));
    }

    @Test
    @DisplayName("HierarchyTemplate generates category tree with cycle detection")
    void testHierarchyTemplateCategoryTree() {
        CteQuery query = HierarchyTemplate.buildCategoryTreeQuery(
                new HierarchyTemplate.CategoryTreeConfig("category_tree", "item_categories", "cat_id", "parent_cat_id", "cat_title", "parent_cat_id IS NULL", true)
        );

        assertTrue(query.isRecursive());
        String sql = query.toSql();
        assertTrue(sql.contains("POSITION('/' || CAST(c.cat_id AS VARCHAR(50)) || '/' IN h.path) = 0"));
    }

    @Test
    @DisplayName("Validation and error handling")
    void testValidation() {
        assertThrows(NullPointerException.class, () -> CteBuilder.recursive(null));
        assertThrows(IllegalArgumentException.class, () -> CteBuilder.recursive("   "));

        assertThrows(IllegalStateException.class, () -> CteBuilder.create().finalSelect("SELECT 1"));

        RecursiveCteBuilder builder = CteBuilder.recursive("test");
        assertThrows(IllegalStateException.class, () -> builder.finalSelect("SELECT 1"));

        builder.anchorSelect("SELECT 1");
        assertThrows(IllegalStateException.class, () -> builder.finalSelect("SELECT 1"));
    }
}
