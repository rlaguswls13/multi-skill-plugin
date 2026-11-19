package com.msp.invindex;

import java.util.List;
import java.util.Set;

/**
 * AST Node hierarchy for boolean full-text search queries.
 */
public sealed interface QueryNode permits
        QueryNode.TermNode,
        QueryNode.AndNode,
        QueryNode.OrNode,
        QueryNode.NotNode {

    record TermNode(String term) implements QueryNode {
    }

    record AndNode(List<QueryNode> children) implements QueryNode {
        public AndNode {
            children = List.copyOf(children);
        }
    }

    record OrNode(List<QueryNode> children) implements QueryNode {
        public OrNode {
            children = List.copyOf(children);
        }
    }

    record NotNode(QueryNode child) implements QueryNode {
    }

    /**
     * Extracts all positive search terms contained in this AST branch.
     */
    default void collectPositiveTerms(Set<String> terms) {
        switch (this) {
            case TermNode(String term) -> terms.add(term);
            case AndNode(List<QueryNode> children) -> {
                for (QueryNode child : children) {
                    child.collectPositiveTerms(terms);
                }
            }
            case OrNode(List<QueryNode> children) -> {
                for (QueryNode child : children) {
                    child.collectPositiveTerms(terms);
                }
            }
            case NotNode ignored -> {
                // Not terms are negative filters, do not collect for positive scoring
            }
        }
    }
}
