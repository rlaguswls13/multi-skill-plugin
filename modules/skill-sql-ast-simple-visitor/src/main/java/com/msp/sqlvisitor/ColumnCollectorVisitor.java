package com.msp.sqlvisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Visitor that collects and categorizes column references from projections,
 * WHERE/HAVING filters, JOIN conditions, GROUP BY, and ORDER BY clauses.
 */
public class ColumnCollectorVisitor implements SqlAstVisitor<Void> {

    public enum ColumnContext {
        PROJECTION,
        FILTER,
        GROUP_BY,
        ORDER_BY,
        OTHER
    }

    private ColumnContext currentContext = ColumnContext.OTHER;

    private final List<ColumnRefNode> allColumns = new ArrayList<>();
    private final List<ColumnRefNode> projectedColumns = new ArrayList<>();
    private final List<ColumnRefNode> filterColumns = new ArrayList<>();
    private final List<ColumnRefNode> groupByColumns = new ArrayList<>();
    private final List<ColumnRefNode> orderByColumns = new ArrayList<>();

    @Override
    public Void visit(SelectQueryNode node) {
        // 1. Projections
        currentContext = ColumnContext.PROJECTION;
        for (SqlNode proj : node.projections()) {
            proj.accept(this);
        }

        // 2. Joins conditions
        currentContext = ColumnContext.FILTER;
        for (JoinNode join : node.joins()) {
            join.accept(this);
        }

        // 3. WHERE clause
        if (node.whereClause() != null) {
            currentContext = ColumnContext.FILTER;
            node.whereClause().accept(this);
        }

        // 4. GROUP BY
        currentContext = ColumnContext.GROUP_BY;
        for (SqlNode group : node.groupBy()) {
            group.accept(this);
        }

        // 5. HAVING
        if (node.havingClause() != null) {
            currentContext = ColumnContext.FILTER;
            node.havingClause().accept(this);
        }

        // 6. ORDER BY
        currentContext = ColumnContext.ORDER_BY;
        for (OrderByNode order : node.orderBy()) {
            order.accept(this);
        }

        currentContext = ColumnContext.OTHER;
        return null;
    }

    @Override
    public Void visit(ColumnRefNode node) {
        allColumns.add(node);
        switch (currentContext) {
            case PROJECTION -> projectedColumns.add(node);
            case FILTER -> filterColumns.add(node);
            case GROUP_BY -> groupByColumns.add(node);
            case ORDER_BY -> orderByColumns.add(node);
            default -> {}
        }
        return null;
    }

    @Override
    public Void visit(BinaryOpNode node) {
        node.left().accept(this);
        node.right().accept(this);
        return null;
    }

    @Override
    public Void visit(JoinNode node) {
        if (node.condition() != null) {
            node.condition().accept(this);
        }
        return null;
    }

    @Override
    public Void visit(OrderByNode node) {
        node.expression().accept(this);
        return null;
    }

    @Override
    public Void visit(InListNode node) {
        node.expression().accept(this);
        for (SqlNode val : node.values()) {
            val.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(FunctionCallNode node) {
        for (SqlNode arg : node.arguments()) {
            arg.accept(this);
        }
        return null;
    }

    public List<ColumnRefNode> getAllColumns() {
        return Collections.unmodifiableList(allColumns);
    }

    public Set<String> getAllColumnNames() {
        Set<String> set = new LinkedHashSet<>();
        for (ColumnRefNode col : allColumns) {
            set.add(col.getFullName());
        }
        return Collections.unmodifiableSet(set);
    }

    public List<ColumnRefNode> getProjectedColumns() {
        return Collections.unmodifiableList(projectedColumns);
    }

    public List<ColumnRefNode> getFilterColumns() {
        return Collections.unmodifiableList(filterColumns);
    }

    public List<ColumnRefNode> getGroupByColumns() {
        return Collections.unmodifiableList(groupByColumns);
    }

    public List<ColumnRefNode> getOrderByColumns() {
        return Collections.unmodifiableList(orderByColumns);
    }
}
