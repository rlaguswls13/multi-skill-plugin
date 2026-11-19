package com.msp.sqlcte;

import java.util.Objects;

/**
 * Pre-configured hierarchical query generators for common tree/graph structures
 * such as organizational charts and category trees.
 */
public final class HierarchyTemplate {

    private HierarchyTemplate() {}

    /**
     * Configuration for generating an organizational chart hierarchy query.
     *
     * @param cteName CTE name (e.g., "org_tree")
     * @param tableName source table name (e.g., "employees")
     * @param idColumn employee ID column (e.g., "emp_id")
     * @param parentIdColumn manager ID column (e.g., "manager_id")
     * @param nameColumn display name column (e.g., "emp_name")
     * @param rootCondition root selection condition (e.g., "manager_id IS NULL")
     * @param maxDepth optional maximum traversal depth (<=0 for unlimited)
     */
    public record OrgChartConfig(
            String cteName,
            String tableName,
            String idColumn,
            String parentIdColumn,
            String nameColumn,
            String rootCondition,
            int maxDepth
    ) {
        public OrgChartConfig {
            cteName = (cteName == null || cteName.isBlank()) ? "org_tree" : cteName;
            Objects.requireNonNull(tableName, "tableName cannot be null");
            Objects.requireNonNull(idColumn, "idColumn cannot be null");
            Objects.requireNonNull(parentIdColumn, "parentIdColumn cannot be null");
            Objects.requireNonNull(nameColumn, "nameColumn cannot be null");
            rootCondition = (rootCondition == null || rootCondition.isBlank()) ? parentIdColumn + " IS NULL" : rootCondition;
        }

        public static OrgChartConfig of(String tableName, String idColumn, String parentIdColumn, String nameColumn) {
            return new OrgChartConfig("org_tree", tableName, idColumn, parentIdColumn, nameColumn, parentIdColumn + " IS NULL", 0);
        }
    }

    /**
     * Builds a full organizational chart recursive CTE query with depth and breadcrumb path tracking.
     */
    public static CteQuery buildOrgChartQuery(OrgChartConfig config) {
        String anchor = "SELECT " + config.idColumn() + ", " + config.parentIdColumn() + ", " + config.nameColumn() +
                ", 1 AS depth, CAST(" + config.idColumn() + " AS VARCHAR(1000)) AS path" +
                " FROM " + config.tableName() +
                " WHERE " + config.rootCondition();

        String recursive = "SELECT e." + config.idColumn() + ", e." + config.parentIdColumn() + ", e." + config.nameColumn() +
                ", t.depth + 1, t.path || '/' || CAST(e." + config.idColumn() + " AS VARCHAR(50))" +
                " FROM " + config.tableName() + " e" +
                " JOIN " + config.cteName() + " t ON e." + config.parentIdColumn() + " = t." + config.idColumn();

        RecursiveCteBuilder builder = CteBuilder.recursive(config.cteName(),
                        config.idColumn(), config.parentIdColumn(), config.nameColumn(), "depth", "path")
                .anchorSelect(anchor)
                .unionAll()
                .recursiveSelect(recursive);

        if (config.maxDepth() > 0) {
            builder.maxDepth("t.depth", config.maxDepth());
        }

        return builder.finalSelect("SELECT * FROM " + config.cteName() + " ORDER BY depth, " + config.idColumn());
    }

    /**
     * Configuration for category tree traversal.
     */
    public record CategoryTreeConfig(
            String cteName,
            String tableName,
            String idColumn,
            String parentIdColumn,
            String nameColumn,
            String rootCondition,
            boolean detectCycles
    ) {
        public CategoryTreeConfig {
            cteName = (cteName == null || cteName.isBlank()) ? "category_hierarchy" : cteName;
            Objects.requireNonNull(tableName, "tableName cannot be null");
            Objects.requireNonNull(idColumn, "idColumn cannot be null");
            Objects.requireNonNull(parentIdColumn, "parentIdColumn cannot be null");
            Objects.requireNonNull(nameColumn, "nameColumn cannot be null");
            rootCondition = (rootCondition == null || rootCondition.isBlank()) ? parentIdColumn + " IS NULL" : rootCondition;
        }
    }

    /**
     * Builds a category tree query with cycle avoidance.
     */
    public static CteQuery buildCategoryTreeQuery(CategoryTreeConfig config) {
        String anchor = "SELECT " + config.idColumn() + ", " + config.parentIdColumn() + ", " + config.nameColumn() +
                ", 0 AS level, '/' || CAST(" + config.idColumn() + " AS VARCHAR(50)) || '/' AS path" +
                " FROM " + config.tableName() +
                " WHERE " + config.rootCondition();

        String recursive = "SELECT c." + config.idColumn() + ", c." + config.parentIdColumn() + ", c." + config.nameColumn() +
                ", h.level + 1, h.path || CAST(c." + config.idColumn() + " AS VARCHAR(50)) || '/'" +
                " FROM " + config.tableName() + " c" +
                " JOIN " + config.cteName() + " h ON c." + config.parentIdColumn() + " = h." + config.idColumn();

        RecursiveCteBuilder builder = CteBuilder.recursive(config.cteName(),
                        config.idColumn(), config.parentIdColumn(), config.nameColumn(), "level", "path")
                .anchorSelect(anchor)
                .unionAll()
                .recursiveSelect(recursive);

        if (config.detectCycles()) {
            builder.detectCycleByPath("h.path", "c." + config.idColumn());
        }

        return builder.finalSelect("SELECT * FROM " + config.cteName() + " ORDER BY path");
    }
}
