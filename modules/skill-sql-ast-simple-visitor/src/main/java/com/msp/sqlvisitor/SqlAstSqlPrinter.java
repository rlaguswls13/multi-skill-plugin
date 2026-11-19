package com.msp.sqlvisitor;

import java.util.stream.Collectors;

/**
 * Visitor that formats an AST into a clean, normalized SQL string.
 */
public class SqlAstSqlPrinter implements SqlAstVisitor<String> {

    @Override
    public String visit(SelectQueryNode node) {
        StringBuilder sb = new StringBuilder("SELECT ");

        if (node.projections().isEmpty()) {
            sb.append("*");
        } else {
            sb.append(node.projections().stream()
                    .map(p -> p.accept(this))
                    .collect(Collectors.joining(", ")));
        }

        if (node.fromTable() != null) {
            sb.append(" FROM ").append(node.fromTable().accept(this));
        }

        for (JoinNode join : node.joins()) {
            sb.append(" ").append(join.accept(this));
        }

        if (node.whereClause() != null) {
            sb.append(" WHERE ").append(node.whereClause().accept(this));
        }

        if (!node.groupBy().isEmpty()) {
            sb.append(" GROUP BY ").append(node.groupBy().stream()
                    .map(g -> g.accept(this))
                    .collect(Collectors.joining(", ")));
        }

        if (node.havingClause() != null) {
            sb.append(" HAVING ").append(node.havingClause().accept(this));
        }

        if (!node.orderBy().isEmpty()) {
            sb.append(" ORDER BY ").append(node.orderBy().stream()
                    .map(o -> o.accept(this))
                    .collect(Collectors.joining(", ")));
        }

        if (node.limit() != null) {
            sb.append(" LIMIT ").append(node.limit());
        }

        if (node.offset() != null) {
            sb.append(" OFFSET ").append(node.offset());
        }

        return sb.toString();
    }

    @Override
    public String visit(TableRefNode node) {
        StringBuilder sb = new StringBuilder(node.getQualifiedName());
        if (node.alias() != null && !node.alias().isBlank()) {
            sb.append(" ").append(node.alias());
        }
        return sb.toString();
    }

    @Override
    public String visit(ColumnRefNode node) {
        return node.getFullName();
    }

    @Override
    public String visit(WildcardNode node) {
        return node.getExpression();
    }

    @Override
    public String visit(LiteralNode node) {
        return node.asSqlLiteral();
    }

    @Override
    public String visit(BinaryOpNode node) {
        String leftStr = node.left().accept(this);
        String rightStr = node.right().accept(this);
        if (node.isLogical()) {
            return "(" + leftStr + " " + node.operator() + " " + rightStr + ")";
        }
        return leftStr + " " + node.operator() + " " + rightStr;
    }

    @Override
    public String visit(JoinNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.type().name()).append(" JOIN ").append(node.table().accept(this));
        if (node.condition() != null) {
            sb.append(" ON ").append(node.condition().accept(this));
        }
        return sb.toString();
    }

    @Override
    public String visit(OrderByNode node) {
        return node.expression().accept(this) + " " + node.direction().name();
    }

    @Override
    public String visit(InListNode node) {
        String expr = node.expression().accept(this);
        String list = node.values().stream().map(v -> v.accept(this)).collect(Collectors.joining(", "));
        return expr + (node.notIn() ? " NOT IN (" : " IN (") + list + ")";
    }

    @Override
    public String visit(FunctionCallNode node) {
        String args = node.arguments().stream().map(a -> a.accept(this)).collect(Collectors.joining(", "));
        return node.functionName() + "(" + args + ")";
    }
}
