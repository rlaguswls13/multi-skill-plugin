package com.msp.sqlvisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Visitor that collects all tables referenced across FROM and JOIN clauses.
 */
public class TableCollectorVisitor implements SqlAstVisitor<Void> {

    private final List<TableRefNode> tables = new ArrayList<>();
    private final Set<String> tableNames = new LinkedHashSet<>();
    private final Map<String, String> aliasToTableMap = new LinkedHashMap<>();

    @Override
    public Void visit(SelectQueryNode node) {
        if (node.fromTable() != null) {
            node.fromTable().accept(this);
        }
        for (JoinNode join : node.joins()) {
            join.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(TableRefNode node) {
        tables.add(node);
        tableNames.add(node.getQualifiedName());
        if (node.alias() != null && !node.alias().isBlank()) {
            aliasToTableMap.put(node.alias(), node.getQualifiedName());
        }
        return null;
    }

    @Override
    public Void visit(JoinNode node) {
        if (node.table() != null) {
            node.table().accept(this);
        }
        return null;
    }

    public List<TableRefNode> getTables() {
        return Collections.unmodifiableList(tables);
    }

    public Set<String> getTableNames() {
        return Collections.unmodifiableSet(tableNames);
    }

    public Map<String, String> getAliasToTableMap() {
        return Collections.unmodifiableMap(aliasToTableMap);
    }
}
