package com.msp.template.parser;

import com.msp.template.TemplateException;
import com.msp.template.node.*;

import java.util.*;

/**
 * Parser that compiles template strings into an AST of {@link TemplateNode}s.
 */
public final class TemplateParser {

    private final String input;
    private int pos;

    private TemplateParser(String input) {
        this.input = Objects.requireNonNull(input, "input template must not be null");
        this.pos = 0;
    }

    /**
     * Parses the template string into a list of AST nodes.
     */
    public static List<TemplateNode> parse(String template) {
        TemplateParser parser = new TemplateParser(template);
        return parser.parseBlock(Collections.emptySet()).nodes();
    }

    private record BlockResult(List<TemplateNode> nodes, String stopTag) {}

    private BlockResult parseBlock(Set<String> stopTags) {
        List<TemplateNode> nodes = new ArrayList<>();
        StringBuilder textAccumulator = new StringBuilder();
        int len = input.length();

        while (pos < len) {
            // Handle escaped variable syntax: \${
            if (input.charAt(pos) == '\\' && pos + 2 < len && input.charAt(pos + 1) == '$' && input.charAt(pos + 2) == '{') {
                textAccumulator.append("${");
                pos += 3;
                continue;
            }

            // Check for tag start ${
            if (pos + 1 < len && input.charAt(pos) == '$' && input.charAt(pos + 1) == '{') {
                int close = input.indexOf('}', pos + 2);
                if (close == -1) {
                    throw new TemplateException("Unclosed tag starting at character position " + pos);
                }

                String tag = input.substring(pos + 2, close).trim();

                // Check if this tag matches one of the expected stop tags
                if (stopTags.contains(tag)) {
                    if (!textAccumulator.isEmpty()) {
                        nodes.add(new TextNode(textAccumulator.toString()));
                        textAccumulator.setLength(0);
                    }
                    pos = close + 1;
                    return new BlockResult(nodes, tag);
                }

                // If this is an unexpected stop tag, throw error
                if ("else".equals(tag) || "endif".equals(tag) || "endeach".equals(tag)) {
                    throw new TemplateException("Unexpected tag '${" + tag + "}' at character position " + pos);
                }

                if (!textAccumulator.isEmpty()) {
                    nodes.add(new TextNode(textAccumulator.toString()));
                    textAccumulator.setLength(0);
                }

                pos = close + 1;

                if (tag.startsWith("if:")) {
                    String condition = tag.substring(3).trim();
                    BlockResult thenResult = parseBlock(Set.of("else", "endif"));
                    List<TemplateNode> thenNodes = thenResult.nodes();
                    List<TemplateNode> elseNodes = List.of();

                    if ("else".equals(thenResult.stopTag())) {
                        BlockResult elseResult = parseBlock(Set.of("endif"));
                        elseNodes = elseResult.nodes();
                    }
                    nodes.add(new ConditionalNode(condition, thenNodes, elseNodes));
                } else if (tag.startsWith("each:")) {
                    String eachSpec = tag.substring(5).trim();
                    String collectionExpr = eachSpec;
                    String alias = null;
                    int asIdx = eachSpec.indexOf(" as ");
                    if (asIdx > 0) {
                        collectionExpr = eachSpec.substring(0, asIdx).trim();
                        alias = eachSpec.substring(asIdx + 4).trim();
                    }
                    BlockResult bodyResult = parseBlock(Set.of("endeach"));
                    nodes.add(new EachNode(collectionExpr, alias, bodyResult.nodes()));
                } else {
                    // Variable interpolation ${expr} or ${expr:spec}
                    int colonIdx = tag.indexOf(':');
                    String expr = colonIdx >= 0 ? tag.substring(0, colonIdx).trim() : tag;
                    String spec = colonIdx >= 0 ? tag.substring(colonIdx + 1).trim() : null;
                    nodes.add(new VariableNode(expr, spec));
                }
                continue;
            }

            textAccumulator.append(input.charAt(pos++));
        }

        if (!stopTags.isEmpty()) {
            throw new TemplateException("Unclosed block, expected one of " + stopTags + " before end of template");
        }

        if (!textAccumulator.isEmpty()) {
            nodes.add(new TextNode(textAccumulator.toString()));
        }

        return new BlockResult(nodes, null);
    }
}
