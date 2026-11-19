# skill-sql-cte-recursive-query-builder

SQL Common Table Expression (CTE) & `WITH RECURSIVE` Hierarchical Query Generator in pure Java 21.

## 🚀 Features

- **ANSI SQL Standards Compliance**: Full support for standard non-recursive `WITH` and recursive `WITH RECURSIVE` queries.
- **Hierarchical Graph / Tree Traversal**:
  - Employee -> Manager organizational charts.
  - Parent -> Child category trees.
  - Breadcrumb path tracking (e.g., string concatenation `'/' || id || '/'`).
  - Depth / level increments (`depth + 1`).
- **Cycle Detection & Infinite Loop Guard**:
  - Standard ANSI SQL:2016 `CYCLE col SET is_cycle TO 'Y' DEFAULT 'N' USING path`.
  - Path containment check: `POSITION('/' || CAST(id AS VARCHAR) || '/' IN path) = 0`, `INSTR`, `NOT LIKE`.
  - Max depth traversal limit: `depth < 100`.
- **Fluent & Composable Builder**:
  - Intuitive chaining: `CteBuilder.recursive("org_tree").anchorSelect(...).unionAll(...).recursiveSelect(...).finalSelect(...)`.
  - Multi-CTE support: seamlessly combine standard and recursive CTEs in one query.
- **High-Level Presets**: `HierarchyTemplate.buildOrgChartQuery(...)` and `HierarchyTemplate.buildCategoryTreeQuery(...)`.
- **Zero Runtime Dependencies**: Written in pure Java 21 standard library.

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation project(':modules:skill-sql-cte-recursive-query-builder')
}
```

## 💡 Quick Start

### 1. Simple Recursive CTE (Tree / Hierarchy)

```java
import com.msp.sqlcte.CteBuilder;
import com.msp.sqlcte.CteQuery;

public class Example {
    public static void main(String[] args) {
        CteQuery query = CteBuilder.recursive("org_tree", "emp_id", "mgr_id", "emp_name", "depth")
                .anchorSelect("""
                    SELECT emp_id, mgr_id, emp_name, 1 AS depth
                    FROM employees
                    WHERE mgr_id IS NULL
                    """)
                .unionAll()
                .recursiveSelect("""
                    SELECT e.emp_id, e.mgr_id, e.emp_name, o.depth + 1
                    FROM employees e
                    JOIN org_tree o ON e.mgr_id = o.emp_id
                    """)
                .finalSelect("SELECT * FROM org_tree ORDER BY depth, emp_id");

        System.out.println(query.toSql());
    }
}
```

### 2. Cycle Detection with Path Tracking

```java
CteQuery query = CteBuilder.recursive("graph_traversal")
        .columns("node_id", "parent_id", "path")
        .anchorSelect("SELECT id, parent_id, '/' || CAST(id AS VARCHAR(50)) || '/' FROM nodes WHERE parent_id IS NULL")
        .unionAll()
        .recursiveSelect("SELECT n.id, n.parent_id, g.path || CAST(n.id AS VARCHAR(50)) || '/' FROM nodes n JOIN graph_traversal g ON n.parent_id = g.node_id")
        .detectCycleByPath("g.path", "n.id")
        .finalSelect("SELECT * FROM graph_traversal");
```

### 3. High-Level Hierarchy Template

```java
import com.msp.sqlcte.HierarchyTemplate;
import com.msp.sqlcte.HierarchyTemplate.OrgChartConfig;

CteQuery orgChart = HierarchyTemplate.buildOrgChartQuery(
        new OrgChartConfig("org_chart", "employees", "emp_id", "manager_id", "full_name", "manager_id IS NULL", 10)
);
System.out.println(orgChart.toSql());
```
