package com.msp.sqlvisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AST Visitor that inspects SQL queries for SQL injection indicators,
 * such as literal equality tautologies ('1'='1', 1=1), tautological OR branches,
 * self-column comparisons (id=id), and always-true predicate bypasses.
 */
public class SqlInjectionGuardVisitor implements SqlAstVisitor<Void> {

    private final List<SecurityAlert> alerts = new ArrayList<>();

    @Override
    public Void visit(SelectQueryNode node) {
        if (node.whereClause() != null) {
            node.whereClause().accept(this);
        }
        for (JoinNode join : node.joins()) {
            join.accept(this);
        }
        if (node.havingClause() != null) {
            node.havingClause().accept(this);
        }
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
    public Void visit(BinaryOpNode node) {
        String op = node.operator().toUpperCase();

        // 1. Check OR clause with always-true condition (e.g. col = 'x' OR 1=1)
        if ("OR".equals(op)) {
            if (isTautology(node.left()) || isAlwaysTrue(node.left())) {
                alerts.add(new SecurityAlert(
                        SecurityAlert.Severity.HIGH,
                        "OR_TAUTOLOGY_BYPASS",
                        "OR clause contains an always-true left operand which may bypass security filters",
                        node.left()
                ));
            }
            if (isTautology(node.right()) || isAlwaysTrue(node.right())) {
                alerts.add(new SecurityAlert(
                        SecurityAlert.Severity.HIGH,
                        "OR_TAUTOLOGY_BYPASS",
                        "OR clause contains an always-true right operand which may bypass security filters",
                        node.right()
                ));
            }
        }

        // 2. Check literal equality tautologies: 1 = 1, 'admin' = 'admin'
        if ("=".equals(op)) {
            if (node.left() instanceof LiteralNode l1 && node.right() instanceof LiteralNode l2) {
                if (Objects.equals(l1.value(), l2.value())) {
                    alerts.add(new SecurityAlert(
                            SecurityAlert.Severity.HIGH,
                            "LITERAL_TAUTOLOGY_EQUAL",
                            "Detected literal equality tautology (" + l1.asSqlLiteral() + " = " + l2.asSqlLiteral() + ")",
                            node
                    ));
                }
            } else if (node.left() instanceof ColumnRefNode c1 && node.right() instanceof ColumnRefNode c2) {
                if (Objects.equals(c1.tableAlias(), c2.tableAlias()) && Objects.equals(c1.columnName(), c2.columnName())) {
                    alerts.add(new SecurityAlert(
                            SecurityAlert.Severity.MEDIUM,
                            "COLUMN_SELF_COMPARE_TAUTOLOGY",
                            "Detected column self-comparison tautology (" + c1.getFullName() + " = " + c2.getFullName() + ")",
                            node
                    ));
                }
            }
        }

        // 3. Check literal inequality tautologies: 1 != 2, 'a' <> 'b'
        if ("!=".equals(op) || "<>".equals(op)) {
            if (node.left() instanceof LiteralNode l1 && node.right() instanceof LiteralNode l2) {
                if (!Objects.equals(l1.value(), l2.value())) {
                    alerts.add(new SecurityAlert(
                            SecurityAlert.Severity.HIGH,
                            "LITERAL_TAUTOLOGY_NOT_EQUAL",
                            "Detected literal inequality tautology (" + l1.asSqlLiteral() + " " + op + " " + l2.asSqlLiteral() + ")",
                            node
                    ));
                }
            }
        }

        // Recurse into children
        node.left().accept(this);
        node.right().accept(this);
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

    private boolean isTautology(SqlNode node) {
        if (node instanceof BinaryOpNode binary) {
            String op = binary.operator().toUpperCase();
            if ("=".equals(op)) {
                if (binary.left() instanceof LiteralNode l1 && binary.right() instanceof LiteralNode l2) {
                    return Objects.equals(l1.value(), l2.value());
                }
                if (binary.left() instanceof ColumnRefNode c1 && binary.right() instanceof ColumnRefNode c2) {
                    return Objects.equals(c1.tableAlias(), c2.tableAlias()) && Objects.equals(c1.columnName(), c2.columnName());
                }
            }
            if ("!=".equals(op) || "<>".equals(op)) {
                if (binary.left() instanceof LiteralNode l1 && binary.right() instanceof LiteralNode l2) {
                    return !Objects.equals(l1.value(), l2.value());
                }
            }
        }
        return false;
    }

    private boolean isAlwaysTrue(SqlNode node) {
        if (node instanceof LiteralNode lit) {
            return lit.type() == LiteralType.BOOLEAN && Boolean.TRUE.equals(lit.value());
        }
        return false;
    }

    public List<SecurityAlert> getAlerts() {
        return Collections.unmodifiableList(alerts);
    }

    public boolean isSafe() {
        return alerts.isEmpty();
    }
}
