package com.msp.sqlvisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Root AST node representing a SELECT query.
 */
public record SelectQueryNode(
        List<SqlNode> projections,
        TableRefNode fromTable,
        List<JoinNode> joins,
        SqlNode whereClause,
        List<SqlNode> groupBy,
        SqlNode havingClause,
        List<OrderByNode> orderBy,
        Integer limit,
        Integer offset
) implements SqlNode {

    public SelectQueryNode {
        projections = projections != null ? List.copyOf(projections) : Collections.emptyList();
        joins = joins != null ? List.copyOf(joins) : Collections.emptyList();
        groupBy = groupBy != null ? List.copyOf(groupBy) : Collections.emptyList();
        orderBy = orderBy != null ? List.copyOf(orderBy) : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }

    public static final class Builder {
        private final List<SqlNode> projections = new ArrayList<>();
        private TableRefNode fromTable;
        private final List<JoinNode> joins = new ArrayList<>();
        private SqlNode whereClause;
        private final List<SqlNode> groupBy = new ArrayList<>();
        private SqlNode havingClause;
        private final List<OrderByNode> orderBy = new ArrayList<>();
        private Integer limit;
        private Integer offset;

        public Builder select(SqlNode... nodes) {
            if (nodes != null) {
                for (SqlNode n : nodes) {
                    if (n != null) projections.add(n);
                }
            }
            return this;
        }

        public Builder select(String... columnNames) {
            if (columnNames != null) {
                for (String col : columnNames) {
                    if (col != null && !col.isBlank()) {
                        if ("*".equals(col.trim())) {
                            projections.add(new WildcardNode());
                        } else if (col.contains(".")) {
                            String[] parts = col.split("\\.", 2);
                            if ("*".equals(parts[1].trim())) {
                                projections.add(new WildcardNode(parts[0].trim()));
                            } else {
                                projections.add(new ColumnRefNode(parts[0].trim(), parts[1].trim()));
                            }
                        } else {
                            projections.add(new ColumnRefNode(col.trim()));
                        }
                    }
                }
            }
            return this;
        }

        public Builder from(TableRefNode table) {
            this.fromTable = table;
            return this;
        }

        public Builder from(String tableName) {
            this.fromTable = new TableRefNode(tableName);
            return this;
        }

        public Builder from(String tableName, String alias) {
            this.fromTable = new TableRefNode(tableName, alias);
            return this;
        }

        public Builder join(JoinNode joinNode) {
            if (joinNode != null) joins.add(joinNode);
            return this;
        }

        public Builder join(JoinType type, TableRefNode table, SqlNode condition) {
            this.joins.add(new JoinNode(type, table, condition));
            return this;
        }

        public Builder where(SqlNode condition) {
            this.whereClause = condition;
            return this;
        }

        public Builder groupBy(SqlNode... nodes) {
            if (nodes != null) {
                for (SqlNode n : nodes) {
                    if (n != null) groupBy.add(n);
                }
            }
            return this;
        }

        public Builder having(SqlNode condition) {
            this.havingClause = condition;
            return this;
        }

        public Builder orderBy(OrderByNode... nodes) {
            if (nodes != null) {
                for (OrderByNode n : nodes) {
                    if (n != null) orderBy.add(n);
                }
            }
            return this;
        }

        public Builder orderBy(String columnName, SortDirection direction) {
            this.orderBy.add(new OrderByNode(new ColumnRefNode(columnName), direction));
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(Integer offset) {
            this.offset = offset;
            return this;
        }

        public SelectQueryNode build() {
            return new SelectQueryNode(projections, fromTable, joins, whereClause, groupBy, havingClause, orderBy, limit, offset);
        }
    }
}
